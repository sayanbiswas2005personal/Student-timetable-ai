package com.college.timetable.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI description of the REST API, served at {@code /v3/api-docs} with a browsable UI at
 * {@code /swagger-ui.html}. Both require an authenticated session.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI collegeTimetableOpenApi(CollegeProperties properties) {
        return new OpenAPI()
                .info(new Info()
                        .title(properties.getName() + " API")
                        .version("1.0.0")
                        .description("""
                                Timetable lookup for college staff.

                                The lookup endpoints answer one question: which class is this student
                                scheduled to attend at this moment. They report the expected schedule
                                only. A timetable cannot show whether a student is present, absent or
                                has permission, and no endpoint claims otherwise.

                                Authentication uses a server side session cookie. Obtain a CSRF token
                                from `GET /api/auth/csrf`, then send it in the `X-XSRF-TOKEN` header
                                on every POST, PUT, PATCH or DELETE.
                                """)
                        .contact(new Contact().name("College timetable system administrator"))
                        .license(new License().name("Proprietary")))
                .components(new Components().addSecuritySchemes("session",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("Session cookie set by POST /api/auth/login")));
    }
}