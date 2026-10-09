package com.college.timetable.dto.catalog;

import java.time.Instant;

import com.college.timetable.entity.Room;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Room", description = "A classroom or laboratory")
public record RoomDto(Long id, String roomCode, String building, String floor,
                      Instant createdAt, Instant updatedAt) {
    public static RoomDto from(Room r) {
        return new RoomDto(r.getId(), r.getRoomCode(), r.getBuilding(), r.getFloor(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
