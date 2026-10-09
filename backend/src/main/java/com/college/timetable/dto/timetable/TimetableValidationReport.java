package com.college.timetable.dto.timetable;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TimetableValidationReport", description = "Why a version cannot be published yet")
public record TimetableValidationReport(boolean publishable, List<ValidationIssue> issues) {

    @Schema(name = "ValidationIssue", description = "A single blocking or advisory problem")
    public record ValidationIssue(String severity, String message, Long entryId) {
    }
}
