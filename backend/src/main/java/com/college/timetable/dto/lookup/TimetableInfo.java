package com.college.timetable.dto.lookup;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/** Which timetable version was used for the answer. */
@Schema(name = "TimetableInfo", description = "The published version the answer was derived from")
public record TimetableInfo(
        Long id,
        int version,
        @Schema(description = "DRAFT, UNDER_REVIEW, PUBLISHED or SUPERSEDED") String status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String sourceFilename) {
}