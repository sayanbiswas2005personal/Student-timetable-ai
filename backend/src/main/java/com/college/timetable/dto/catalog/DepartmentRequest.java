package com.college.timetable.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DepartmentRequest", description = "Create or update a department")
public record DepartmentRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 20) String code,
        Boolean active) {
}
