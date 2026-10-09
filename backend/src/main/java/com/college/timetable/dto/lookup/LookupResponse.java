package com.college.timetable.dto.lookup;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of "which class should this student be attending right now".
 *
 * <p>The payload describes the expected schedule only. It never asserts that a student is
 * present, absent, or breaking any rule, because a timetable cannot establish any of that.
 */
@Schema(name = "LookupResponse", description = "Timetable lookup result")
public record LookupResponse(
        LookupStatus status,
        @Schema(description = "Calm, factual sentence describing the expected schedule")
        String message,
        @Schema(description = "The instant the answer was computed for, in UTC")
        Instant evaluatedAt,
        @Schema(example = "Asia/Kolkata") String collegeTimezone,
        @Schema(example = "2026-10-09") String collegeDate,
        @Schema(example = "10:32") String collegeTime,
        @Schema(example = "FRIDAY") String dayName,
        StudentSummary student,
        @Schema(description = "The period containing the requested instant, if any")
        ClassInfo currentClass,
        @Schema(description = "The next scheduled class, if any")
        ClassInfo nextClass,
        @Schema(description = "True when nextClass falls on a later day than the current one")
        boolean nextClassOnLaterDay,
        TimetableInfo timetable,
        @Schema(description = "Data quality remarks, for example detected overlaps")
        List<String> notices,
        @Schema(description = "Populated only for AMBIGUOUS_SEARCH, listing the choices to present")
        List<AmbiguityOption> options,
        @Schema(description = "Populated only for TIMETABLE_CONFLICT, listing every entry claiming this instant")
        List<ClassInfo> conflictingClasses) {

    public static LookupResponse of(LookupStatus status, String message, Instant evaluatedAt,
                                    String timezone, String date, String time, String dayName) {
        return new LookupResponse(status, message, evaluatedAt, timezone, date, time, dayName,
                null, null, null, false, null, List.of(), List.of(), List.of());
    }
}