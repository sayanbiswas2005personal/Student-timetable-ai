package com.college.timetable.dto.timetable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.college.timetable.entity.Timetable;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Timetable", description = "A versioned weekly timetable for one section")
public record TimetableDto(
        Long id,
        Long sectionId,
        String sectionName,
        String programName,
        Long academicTermId,
        String academicYear,
        int semesterNumber,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        String sourceFilename,
        int version,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        List<TimetableEntryDto> entries) {

    public static TimetableDto from(Timetable t, List<TimetableEntryDto> entries) {
        var section = t.getSection();
        var program = section.getProgram();
        var term = t.getAcademicTerm();
        return new TimetableDto(t.getId(), section.getId(), section.getSectionName(), program.getName(),
                term.getId(), term.getAcademicYear(), term.getSemesterNumber(),
                t.getEffectiveFrom(), t.getEffectiveTo(), t.getStatus().name(), t.getSourceFilename(),
                t.getVersion(), t.getPublishedAt(), t.getCreatedAt(), t.getUpdatedAt(), entries);
    }
}
