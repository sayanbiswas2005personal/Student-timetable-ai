package com.college.timetable.dto.common;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The single error shape returned by every REST endpoint.
 *
 * <p>Production responses never contain stack traces, SQL, or configuration values.
 */
@Schema(name = "ApiError", description = "Uniform error response")
public record ApiErrorResponse(
        @Schema(description = "Machine readable error code, for example STUDENT_NOT_FOUND")
        String code,
        @Schema(description = "Human readable, safe to show to a staff member")
        String message,
        @Schema(description = "Additional field level details, may be empty")
        List<String> details,
        @Schema(description = "Request path that failed")
        String path,
        @Schema(description = "Server timestamp (UTC)")
        Instant timestamp) {
}