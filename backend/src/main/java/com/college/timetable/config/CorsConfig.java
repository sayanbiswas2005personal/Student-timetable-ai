package com.college.timetable.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cross origin settings.
 *
 * <p>Kept in its own configuration class so {@link SecurityConfig} can inject the resulting bean
 * without creating a self reference during context startup.
 *
 * <p>Local development does not need this at all: the Vite dev server proxies {@code /api} to the
 * backend, so the browser sees a single origin. Origins must be listed explicitly for a split
 * deployment, and {@code allowCredentials} stays on because authentication uses a cookie.
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${college.cors.allowed-origins:}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Retry-After"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        if (allowedOrigins.isEmpty()) {
            log.info("No CORS origins configured; the API expects same origin requests "
                    + "(use the Vite dev proxy in development).");
        } else {
            log.info("CORS allowed origins: {}", allowedOrigins);
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}