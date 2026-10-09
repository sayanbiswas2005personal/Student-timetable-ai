package com.college.timetable.dto.student;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UpdateStudentRequest", description = "Editable student fields")
public record UpdateStudentRequest(
        @NotBlank @Size(max = 40) String registrationNumber,
        @Size(max = 150) String fullName,
        @NotNull Long sectionId,
        @NotNull Boolean active) {
}