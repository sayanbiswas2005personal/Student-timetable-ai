package com.college.timetable.dto.timetable;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CreateTimetableRequest", description = "Create a new draft version for a section")
public record CreateTimetableRequest(
        @NotNull Long sectionId,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @Size(max = 255) String sourceFilename) {
}
