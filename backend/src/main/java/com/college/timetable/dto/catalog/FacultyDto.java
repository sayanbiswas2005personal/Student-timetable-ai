package com.college.timetable.dto.catalog;

import java.time.Instant;

import com.college.timetable.entity.Faculty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Faculty", description = "Teaching staff member, optional in a timetable")
public record FacultyDto(Long id, String facultyName, String facultyCode, boolean active,
                         Instant createdAt, Instant updatedAt) {
    public static FacultyDto from(Faculty f) {
        return new FacultyDto(f.getId(), f.getFacultyName(), f.getFacultyCode(), f.isActive(),
                f.getCreatedAt(), f.getUpdatedAt());
    }
}
