package com.college.timetable.dto.lookup;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Whole week view of one section's timetable. */
@Schema(name = "SectionTimetableResponse", description = "Published weekly timetable for a section")
public record SectionTimetableResponse(
        Long sectionId,
        String programName,
        String academicYear,
        int semesterNumber,
        String sectionName,
        TimetableInfo timetable,
        String collegeTimezone,
        @Schema(example = "2026-10-09") String today,
        @Schema(example = "FRIDAY") String todayName,
        @Schema(example = "10:32", description = "College local time, used by the UI to draw the current time indicator")
        String currentTime,
        List<DaySchedule> days,
        @Schema(description = "Whether a published timetable was in force for the requested date")
        LookupStatus status,
        String statusMessage) {

    @Schema(name = "DaySchedule", description = "All periods on one weekday")
    public record DaySchedule(
            int dayOfWeek,
            String dayName,
            List<ClassInfo> periods) {
    }
}