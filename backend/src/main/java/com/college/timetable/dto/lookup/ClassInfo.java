package com.college.timetable.dto.lookup;

import io.swagger.v3.oas.annotations.media.Schema;

/** A single scheduled period. */
@Schema(name = "ClassInfo", description = "One period of a published timetable")
public record ClassInfo(
        @Schema(description = "Subject code as printed in the source timetable, when known")
        String subjectCode,
        String subjectName,
        @Schema(description = "Null when the source timetable does not name the faculty")
        String facultyName,
        @Schema(description = "Null when the source timetable does not give a room")
        String roomCode,
        String roomBuilding,
        int dayOfWeek,
        @Schema(example = "FRIDAY") String dayName,
        @Schema(example = "09:30") String startTime,
        @Schema(example = "10:25") String endTime,
        @Schema(description = "CLASS, BREAK or OTHER") String entryType,
        Long timetableId,
        int timetableVersion) {
}