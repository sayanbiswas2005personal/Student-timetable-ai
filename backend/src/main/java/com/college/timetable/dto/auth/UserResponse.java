package com.college.timetable.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserResponse", description = "The signed in account")
public record UserResponse(
        Long id,
        String username,
        String displayName,
        @Schema(example = "STAFF") String role) {
}