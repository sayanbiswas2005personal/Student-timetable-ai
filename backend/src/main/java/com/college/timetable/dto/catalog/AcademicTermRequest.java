package com.college.timetable.dto.catalog;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AcademicTermRequest", description = "Create or update an academic term")
public record AcademicTermRequest(
        @NotBlank @Size(max = 20) String academicYear,
        @Min(1) @Max(12) int semesterNumber,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        Boolean active) {
}
