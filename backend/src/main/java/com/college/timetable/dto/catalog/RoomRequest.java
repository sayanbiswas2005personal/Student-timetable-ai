package com.college.timetable.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RoomRequest", description = "Create or update a room")
public record RoomRequest(
        @NotBlank @Size(max = 40) String roomCode,
        @Size(max = 100) String building,
        @Size(max = 20) String floor) {
}
