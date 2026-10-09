package com.college.timetable.dto.lookup;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StudentSummary", description = "Verified student identity and section mapping")
public record StudentSummary(
        @Schema(example = "UG/02/BTCSEAIML/2023/024") String registrationNumber,
        @Schema(description = "Verified section, so the UI can link to its weekly timetable")
        Long sectionId,
        @Schema(description = "Present only when the college records a name and the viewer may see it")
        String fullName,
        String departmentName,
        String programName,
        String programCode,
        int semesterNumber,
        String academicYear,
        String sectionName,
        boolean active) {
}