package com.college.timetable.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SubjectRequest", description = "Create or update a subject")
public record SubjectRequest(
        @NotBlank @Size(max = 40) String subjectCode,
        @NotBlank @Size(max = 200) String subjectName,
        Long programId,
        Boolean active) {
}
