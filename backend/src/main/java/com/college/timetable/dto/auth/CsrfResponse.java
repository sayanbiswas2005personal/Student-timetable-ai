package com.college.timetable.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The CSRF token the browser must echo back.
 *
 * <p>The same value is written to the readable {@code XSRF-TOKEN} cookie by Spring Security; the
 * SPA reads that cookie and sends the value in the {@code X-XSRF-TOKEN} header.
 */
@Schema(name = "CsrfResponse", description = "CSRF token for the current session")
public record CsrfResponse(String token, String headerName, String cookieName, String parameterName) {
}