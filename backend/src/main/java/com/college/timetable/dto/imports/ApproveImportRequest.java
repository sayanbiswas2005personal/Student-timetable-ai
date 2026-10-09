package com.college.timetable.dto.imports;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApproveImportRequest",
        description = "Turn approved candidates into a draft timetable for one section")
public record ApproveImportRequest(
        @NotNull Long sectionId,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {
}
