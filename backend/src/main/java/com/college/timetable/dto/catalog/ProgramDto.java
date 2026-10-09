package com.college.timetable.dto.catalog;

import java.time.Instant;

import com.college.timetable.entity.Program;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Program", description = "Degree programme offered by a department")
public record ProgramDto(Long id, Long departmentId, String departmentName, String name, String code,
                         String degreeType, boolean active, Instant createdAt, Instant updatedAt) {
    public static ProgramDto from(Program p) {
        return new ProgramDto(p.getId(), p.getDepartment().getId(), p.getDepartment().getName(),
                p.getName(), p.getCode(), p.getDegreeType(), p.isActive(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
