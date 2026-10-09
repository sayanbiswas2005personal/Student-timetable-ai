package com.college.timetable.dto.timetable;

import java.time.LocalTime;

import com.college.timetable.entity.TimetableEntry;
import com.college.timetable.util.TimeText;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TimetableEntry", description = "One period in a timetable version")
public record TimetableEntryDto(
        Long id,
        Long timetableId,
        int dayOfWeek,
        @Schema(example = "FRIDAY") String dayName,
        @Schema(example = "09:30") String startTime,
        @Schema(example = "10:25") String endTime,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Long facultyId,
        String facultyName,
        Long roomId,
        String roomCode,
        String entryType,
        String verificationStatus,
        String rawSourceText,
        Integer sourcePageNumber) {

    public static TimetableEntryDto from(TimetableEntry entry) {
        var subject = entry.getSubject();
        var faculty = entry.getFaculty();
        var room = entry.getRoom();
        return new TimetableEntryDto(entry.getId(), entry.getTimetable().getId(),
                entry.getDayOfWeek(),
                com.college.timetable.service.lookup.TimetableLookupEngine.dayName(entry.getDayOfWeek()),
                TimeText.format(entry.getStartTime()), TimeText.format(entry.getEndTime()),
                subject == null ? null : subject.getId(),
                subject == null ? null : subject.getSubjectCode(),
                subject == null ? null : subject.getSubjectName(),
                faculty == null ? null : faculty.getId(),
                faculty == null ? null : faculty.getFacultyName(),
                room == null ? null : room.getId(),
                room == null ? null : room.getRoomCode(),
                entry.getEntryType().name(), entry.getVerificationStatus().name(),
                entry.getRawSourceText(), entry.getSourcePageNumber());
    }

    /** Convenience for building an entry in memory before it is persisted. */
    public static String format(LocalTime time) {
        return TimeText.format(time);
    }
}
