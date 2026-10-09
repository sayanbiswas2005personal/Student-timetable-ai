package com.college.timetable.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the single source of truth for "now" and for the college timezone.
 *
 * <p>Every service that needs the current date or time must inject {@link Clock} rather than
 * calling {@code LocalDate.now()} directly, so that tests can pin the clock to a deterministic
 * instant instead of depending on the wall clock.
 */
@Configuration
public class TimeConfig {

    @Bean
    public ZoneId collegeZoneId(CollegeProperties properties) {
        return ZoneId.of(properties.getTimezone());
    }

    /**
     * Clock bound to the configured college timezone. Overridable with a bean of the same name
     * (for example in tests) to obtain a fixed clock.
     */
    @Bean
    public Clock collegeClock(ZoneId collegeZoneId) {
        return Clock.system(collegeZoneId);
    }
}