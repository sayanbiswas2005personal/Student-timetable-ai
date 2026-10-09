package com.college.timetable.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.college.timetable.config.SecurityConfig;

import jakarta.servlet.http.Cookie;

/**
 * Verifies that the API enforces authentication and role separation, and that CSRF stays on.
 *
 * <p>These checks matter because a timetable lookup exposes student data, and the write endpoints
 * change data that staff have to be able to trust.
 *
 * <p>Note on CSRF: the tests deliberately avoid Spring Security's {@code csrf()} helper and instead
 * replay what a browser actually does, by taking the issued token and sending it back in the
 * header and cookie. That keeps the assertions honest about the contract the SPA depends on.
 */
@SpringBootTest(properties = "college.seed-demo-data=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuthorizationTest {

    private static final String BAD_CREDENTIALS =
            "{\"username\":\"nobody\",\"password\":\"wrong-password-here\"}";

    @Autowired
    private MockMvc mockMvc;

    // ------------------------------------------------------- authentication

    @Test
    @DisplayName("every API endpoint requires authentication")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/lookup/clock"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("the health endpoint stays open for uptime checks")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a signed in staff member can read the college clock")
    void staffCanLookup() throws Exception {
        mockMvc.perform(get("/api/lookup/clock").with(user("staff")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"));
    }

    @Test
    @DisplayName("errors never leak a stack trace or framework internals")
    void errorsAreSafe() throws Exception {
        String body = mockMvc.perform(get("/api/lookup/student/does-not-exist").with(user("staff")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("org.springframework")
                .doesNotContain("com.college.timetable")
                .doesNotContain("Exception");
    }

    // -------------------------------------------------------- role based access

    @Test
    @DisplayName("staff cannot create students")
    void staffCannotManageStudents() throws Exception {
        mockMvc.perform(post("/api/students")
                        .with(user("staff"))
                        .with(csrfSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationNumber\":\"X/1\",\"sectionId\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("staff cannot upload timetable PDFs")
    void staffCannotImport() throws Exception {
        var pdf = new MockMultipartFile("file", "timetable.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/imports").file(pdf)
                        .with(user("staff"))
                        .with(csrfSession()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("staff cannot publish a timetable")
    void staffCannotPublish() throws Exception {
        mockMvc.perform(post("/api/timetables/1/publish")
                        .with(user("staff"))
                        .with(csrfSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("staff cannot change a student's status")
    void studentStatusIsAdminOnly() throws Exception {
        mockMvc.perform(patch("/api/students/1/status")
                        .with(user("staff"))
                        .with(csrfSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("staff cannot reach the account administration endpoints")
    void staffCannotReachAdministration() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(user("staff")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("staff can read timetable versions, which the section screen needs")
    void staffCanReadTimetables() throws Exception {
        mockMvc.perform(get("/api/timetables").with(user("staff")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an administrator can reach the administration endpoints")
    void adminCanManage() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ------------------------------------------------------------------- CSRF

    @Test
    @DisplayName("an anonymous write without a CSRF token is stopped before authentication")
    void csrfIsRequiredForAnonymous() throws Exception {
        // The CSRF filter runs before authorization and rejects the request outright, so the
        // status is 403 rather than the 401 an unauthenticated read would produce.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BAD_CREDENTIALS))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("an authenticated write without a CSRF token is forbidden")
    void csrfIsRequiredForSignedInUsers() throws Exception {
        mockMvc.perform(patch("/api/students/1/status")
                        .with(user("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("a write with the issued token in the header reaches the handler")
    void csrfTokenIsAccepted() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrfSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BAD_CREDENTIALS))
                // Fails on the credentials, not at the CSRF layer.
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("the CSRF endpoint issues a token and names the cookie and header")
    void csrfEndpointIssuesAToken() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value(SecurityConfig.CSRF_HEADER))
                .andExpect(jsonPath("$.cookieName").value(SecurityConfig.CSRF_COOKIE));
    }

    @Test
    @DisplayName("the documented cookie and header names are the ones the SPA relies on")
    void csrfContractIsStable() {
        assertThat(SecurityConfig.CSRF_COOKIE).isEqualTo("XSRF-TOKEN");
        assertThat(SecurityConfig.CSRF_HEADER).isEqualTo("X-XSRF-TOKEN");
    }

    // --------------------------------------------------------------- helpers

    private static RequestPostProcessor user(String role) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(role).roles(role.toUpperCase(java.util.Locale.ROOT));
    }

    /**
     * Replays what a browser does for a state changing request: fetch the CSRF token, then send it
     * back in the X-XSRF-TOKEN header together with the XSRF-TOKEN cookie.
     */
    private RequestPostProcessor csrfSession() throws Exception {
        MvcResult bootstrap = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie issued = bootstrap.getResponse().getCookie(SecurityConfig.CSRF_COOKIE);
        String value = issued != null ? issued.getValue() : tokenFromBody(bootstrap);
        return request -> {
            request.addHeader(SecurityConfig.CSRF_HEADER, value);
            request.setCookies(new Cookie(SecurityConfig.CSRF_COOKIE, value));
            return request;
        };
    }

    private static String tokenFromBody(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String marker = "\"token\":\"";
        int start = body.indexOf(marker) + marker.length();
        return body.substring(start, body.indexOf('"', start));
    }
}