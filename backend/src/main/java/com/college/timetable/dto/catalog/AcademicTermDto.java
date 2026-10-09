package com.college.timetable.dto.catalog;

import java.time.Instant;
import java.time.LocalDate;

import com.college.timetable.entity.AcademicTerm;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AcademicTerm", description = "One semester of one academic year")
public record AcademicTermDto(Long id, String academicYear, int semesterNumber,
                              LocalDate startDate, LocalDate endDate, boolean active,
                              Instant createdAt, Instant updatedAt) {
    public static AcademicTermDto from(AcademicTerm t) {
        return new AcademicTermDto(t.getId(), t.getAcademicYear(), t.getSemesterNumber(),
                t.getStartDate(), t.getEndDate(), t.isActive(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
