package com.college.timetable.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProgramRequest", description = "Create or update a programme")
public record ProgramRequest(
        @NotNull Long departmentId,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 30) String code,
        @Size(max = 20) String degreeType,
        Boolean active) {
}
