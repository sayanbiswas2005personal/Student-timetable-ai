package com.college.timetable.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FacultyRequest", description = "Create or update a faculty record")
public record FacultyRequest(
        @NotBlank @Size(max = 150) String facultyName,
        @Size(max = 40) String facultyCode,
        Boolean active) {
}
