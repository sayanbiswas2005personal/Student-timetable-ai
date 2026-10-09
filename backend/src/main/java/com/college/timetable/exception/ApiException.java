package com.college.timetable.exception;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * Application level exception carrying the HTTP status and a stable machine readable code.
 * Anything thrown from a service is translated into a consistent {@code ApiError} body by
 * {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String[] details;

    public ApiException(HttpStatus status, String code, String message, String... details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? new String[0] : details;
    }

    public ApiException(HttpStatus status, String code, String message, List<String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? new String[0] : details.toArray(new String[0]);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String[] getDetails() {
        return details.clone();
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ApiException badRequest(String code, String message, String... details) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, details);
    }

    public static ApiException conflict(String message, String... details) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message, details);
    }

    public static ApiException unprocessable(String code, String message, String... details) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message, details);
    }
}