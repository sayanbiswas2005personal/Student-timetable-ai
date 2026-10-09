package com.college.timetable.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LoginResponse", description = "Session established; the browser now holds an HttpOnly cookie")
public record LoginResponse(UserResponse user, String message) {
}