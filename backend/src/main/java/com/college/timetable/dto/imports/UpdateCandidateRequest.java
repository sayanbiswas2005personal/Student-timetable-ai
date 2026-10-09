package com.college.timetable.dto.imports;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UpdateCandidateRequest",
        description = "Correct one extracted row. Mapping to database ids is what makes the row "
                + "resolvable; free text labels alone are never published.")
public record UpdateCandidateRequest(
        Long sectionId,
        Long subjectId,
        Long facultyId,
        Long roomId,
        @Min(1) @Max(7) Integer dayOfWeek,
        String startTime,
        String endTime,
        String entryType,
        @Size(max = 120) String sectionLabel,
        @Size(max = 300) String subjectLabel,
        @Size(max = 300) String facultyLabel,
        @Size(max = 120) String roomLabel,
        @Size(max = 1000) String reviewerNotes,
        @Size(max = 20) String reviewStatus) {
}
