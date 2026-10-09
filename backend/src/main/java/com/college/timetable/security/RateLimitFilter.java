package com.college.timetable.security;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.college.timetable.config.RateLimitProperties;
import com.college.timetable.dto.common.ApiErrorResponse;
import com.college.timetable.service.security.InMemoryRateLimiter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Throttles login attempts and student lookups.
 *
 * <p>Responses are JSON in exactly the same shape as every other error, so the frontend can
 * display a "too many attempts, try again shortly" message without special handling.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final InMemoryRateLimiter limiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(InMemoryRateLimiter limiter, RateLimitProperties properties,
                           ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String client = clientKey(request);
        InMemoryRateLimiter.Decision decision;

        if (request.getRequestURI().startsWith("/api/auth/login")) {
            decision = limiter.check("login:" + client,
                    properties.getLoginAttempts(), properties.getLoginWindow());
        } else if (request.getRequestURI().startsWith("/api/lookup")) {
            decision = limiter.check("lookup:" + client,
                    properties.getLookupRequests(), properties.getLookupWindow());
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        if (!decision.allowed()) {
            log.info("Rate limit hit for {} on {}", client, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            ApiErrorResponse body = new ApiErrorResponse("RATE_LIMITED",
                    "Too many requests. Please wait a moment and try again.", List.of(),
                    request.getRequestURI(), Instant.now());
            try {
                objectMapper.writeValue(response.getWriter(), body);
            } catch (JsonProcessingException ex) {
                log.warn("Could not serialise rate limit response", ex);
            }
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}