package com.college.timetable.dto.timetable;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TimetableEntryRequest", description = "Add or replace one period")
public record TimetableEntryRequest(
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        Long subjectId,
        Long facultyId,
        Long roomId,
        String entryType,
        @jakarta.validation.constraints.Size(max = 1000) String rawSourceText) {
}
