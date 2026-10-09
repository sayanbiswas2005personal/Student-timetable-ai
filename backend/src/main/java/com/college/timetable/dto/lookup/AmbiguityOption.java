package com.college.timetable.dto.lookup;

import io.swagger.v3.oas.annotations.media.Schema;

/** A verified match the user can choose from when a search resolves to more than one section. */
@Schema(name = "AmbiguityOption", description = "A candidate section returned when a search is ambiguous")
public record AmbiguityOption(
        Long sectionId,
        String label,
        String departmentName,
        String programName,
        String programCode,
        String academicYear,
        int semesterNumber,
        String sectionName) {
}