package com.college.timetable.dto.catalog;

import java.time.Instant;

import com.college.timetable.entity.Subject;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Subject", description = "A teachable subject")
public record SubjectDto(Long id, String subjectCode, String subjectName, Long programId,
                         boolean active, Instant createdAt, Instant updatedAt) {
    public static SubjectDto from(Subject s) {
        return new SubjectDto(s.getId(), s.getSubjectCode(), s.getSubjectName(),
                s.getProgram() == null ? null : s.getProgram().getId(), s.isActive(),
                s.getCreatedAt(), s.getUpdatedAt());
    }
}
