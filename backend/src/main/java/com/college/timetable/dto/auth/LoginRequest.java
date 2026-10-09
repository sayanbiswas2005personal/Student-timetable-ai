package com.college.timetable.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LoginRequest", description = "Credentials for an existing account")
public record LoginRequest(
        @NotBlank @Size(max = 60) String username,
        @NotBlank @Size(max = 200) String password) {
}