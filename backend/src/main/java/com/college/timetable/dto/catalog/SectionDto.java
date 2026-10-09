package com.college.timetable.dto.catalog;

import java.time.Instant;

import com.college.timetable.entity.Section;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Section", description = "A student group within a programme and term")
public record SectionDto(Long id, Long programId, String programName, String programCode,
                         Long academicTermId, String academicYear, int semesterNumber,
                         String sectionName, boolean active, Instant createdAt, Instant updatedAt) {
    public static SectionDto from(Section s) {
        var program = s.getProgram();
        var term = s.getAcademicTerm();
        return new SectionDto(s.getId(), program.getId(), program.getName(), program.getCode(),
                term.getId(), term.getAcademicYear(), term.getSemesterNumber(),
                s.getSectionName(), s.isActive(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
