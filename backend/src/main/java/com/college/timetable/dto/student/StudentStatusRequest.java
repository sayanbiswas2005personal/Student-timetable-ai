package com.college.timetable.dto.student;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StudentStatusRequest", description = "Activate or deactivate a student without touching other fields")
public record StudentStatusRequest(@NotNull Boolean active) {
}