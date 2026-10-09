package com.college.timetable.controller;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.college.timetable.dto.auth.CsrfResponse;
import com.college.timetable.dto.auth.LoginRequest;
import com.college.timetable.dto.auth.LoginResponse;
import com.college.timetable.dto.auth.UserResponse;
import com.college.timetable.entity.AppUser;
import com.college.timetable.exception.ApiException;
import com.college.timetable.repository.AppUserRepository;
import com.college.timetable.security.AppUserPrincipal;
import com.college.timetable.security.CurrentUser;
import com.college.timetable.service.audit.AuditService;
import com.college.timetable.service.security.InMemoryRateLimiter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Login, logout and "who am I". There is no public registration. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AppUserRepository userRepository;
    private final AuditService auditService;
    private final InMemoryRateLimiter rateLimiter;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          AppUserRepository userRepository,
                          AuditService auditService,
                          InMemoryRateLimiter rateLimiter) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/csrf")
    @Operation(summary = "Issue a CSRF token so the SPA can bootstrap a session")
    public CsrfResponse csrf(CsrfToken token) {
        // The cookie and header names are returned from configuration rather than from the token
        // object, so the contract the SPA depends on never drifts with a framework default.
        return new CsrfResponse(token.getToken(),
                com.college.timetable.config.SecurityConfig.CSRF_HEADER,
                com.college.timetable.config.SecurityConfig.CSRF_COOKIE,
                com.college.timetable.config.SecurityConfig.CSRF_PARAMETER);
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with username and password")
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest httpRequest,
                               HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);

            AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
            touchLastLogin(principal.getUsername());
            auditService.recordAs(principal.getId(), principal.getUsername(), "LOGIN_SUCCESS", "AppUser",
                    principal.getId(), "Signed in from " + clientIp(httpRequest));
            rateLimiter.reset("login:" + clientIp(httpRequest));

            return new LoginResponse(toResponse(principal), "Signed in.");
        } catch (DisabledException ex) {
            auditService.recordAs(null, request.username(), "LOGIN_BLOCKED", "AppUser", null,
                    "Account disabled");
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                    "This account has been disabled. Contact an administrator.");
        } catch (BadCredentialsException ex) {
            // The reason is deliberately not logged and never reveals whether the username exists.
            auditService.recordAs(null, request.username(), "LOGIN_FAILURE", "AppUser", null,
                    "Invalid credentials from " + clientIp(httpRequest));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "That username and password combination was not recognised.");
        } catch (AuthenticationException ex) {
            log.debug("Login rejected for {}", request.username());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "That username and password combination was not recognised.");
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "End the current session")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        var principal = CurrentUser.principal();
        principal.ifPresent(p -> auditService.recordAs(p.getId(), p.getUsername(), "LOGOUT", "AppUser",
                p.getId(), "Signed out"));

        HttpServletRequest request = httpRequest;
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        httpResponse.setHeader("Set-Cookie", "JSESSIONID=; Path=/; Max-Age=0; HttpOnly");
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "The currently signed in account")
    public UserResponse me() {
        var principal = CurrentUser.principal()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "Sign in to continue."));
        return toResponse(principal);
    }

    private void touchLastLogin(String username) {
        AppUser user = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (user != null) {
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);
        }
    }

    public static UserResponse toResponse(AppUserPrincipal principal) {
        return new UserResponse(principal.getId(), principal.getUsername(),
                principal.getDisplayName(), principal.getRole());
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}