package com.college.timetable.dto.catalog;

import java.time.Instant;

import com.college.timetable.entity.Department;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Department", description = "Academic department")
public record DepartmentDto(Long id, String name, String code, boolean active,
                            Instant createdAt, Instant updatedAt) {
    public static DepartmentDto from(Department d) {
        return new DepartmentDto(d.getId(), d.getName(), d.getCode(), d.isActive(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
