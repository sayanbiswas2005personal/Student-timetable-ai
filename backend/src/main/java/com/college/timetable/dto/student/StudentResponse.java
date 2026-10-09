package com.college.timetable.dto.student;

import java.time.Instant;

import com.college.timetable.entity.Student;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StudentResponse", description = "A student record with its verified section mapping")
public record StudentResponse(
        Long id,
        String registrationNumber,
        String fullName,
        boolean active,
        Long sectionId,
        String sectionName,
        String programName,
        String programCode,
        String departmentName,
        String academicYear,
        int semesterNumber,
        Instant createdAt,
        Instant updatedAt) {

    public static StudentResponse from(Student student) {
        var section = student.getSection();
        var program = section.getProgram();
        var term = section.getAcademicTerm();
        return new StudentResponse(
                student.getId(),
                student.getRegistrationNumber(),
                student.getFullName(),
                student.isActive(),
                section.getId(),
                section.getSectionName(),
                program.getName(),
                program.getCode(),
                program.getDepartment() == null ? null : program.getDepartment().getName(),
                term.getAcademicYear(),
                term.getSemesterNumber(),
                student.getCreatedAt(),
                student.getUpdatedAt());
    }
}