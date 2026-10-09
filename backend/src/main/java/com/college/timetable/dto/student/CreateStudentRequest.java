package com.college.timetable.dto.student;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CreateStudentRequest", description = "New student with a verified section")
public record CreateStudentRequest(
        @NotBlank @Size(max = 40) String registrationNumber,
        @Size(max = 150) String fullName,
        @NotNull Long sectionId,
        Boolean active) {
}