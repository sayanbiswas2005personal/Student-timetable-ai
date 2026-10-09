package com.college.timetable.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.college.timetable.security.RateLimitFilter;
import com.college.timetable.service.security.InMemoryRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Rate limiting beans.
 *
 * <p>Separate from {@link SecurityConfig} so that the security filter chain can depend on
 * {@link RateLimitFilter} without the configuration class referring to its own bean.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public InMemoryRateLimiter rateLimiter(Clock clock) {
        return new InMemoryRateLimiter(clock);
    }

    @Bean
    public RateLimitFilter rateLimitFilter(InMemoryRateLimiter limiter,
                                           RateLimitProperties properties,
                                           ObjectMapper objectMapper) {
        return new RateLimitFilter(limiter, properties, objectMapper);
    }
}