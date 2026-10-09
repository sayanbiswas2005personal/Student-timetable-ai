package com.college.timetable.dto.imports;

import java.math.BigDecimal;
import java.time.LocalTime;

import com.college.timetable.entity.ImportCandidate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ImportCandidate", description = "One extracted timetable row awaiting administrator review")
public record ImportCandidateDto(
        Long id,
        Long importJobId,
        int pageNumber,
        String rawText,
        String extractedFields,
        BigDecimal confidence,
        String reviewStatus,
        String reviewerNotes,
        String sectionLabel,
        Long sectionId,
        String subjectLabel,
        Long subjectId,
        String facultyLabel,
        Long facultyId,
        String roomLabel,
        Long roomId,
        Integer dayOfWeek,
        String startTime,
        String endTime,
        String entryType,
        String validationErrors) {

    public static ImportCandidateDto from(ImportCandidate c) {
        return new ImportCandidateDto(c.getId(), c.getImportJob().getId(), c.getPageNumber(),
                c.getRawText(), c.getExtractedFields(), c.getConfidence(),
                c.getReviewStatus().name(), c.getReviewerNotes(),
                c.getSectionLabel(), c.getSection() == null ? null : c.getSection().getId(),
                c.getSubjectLabel(), c.getSubject() == null ? null : c.getSubject().getId(),
                c.getFacultyLabel(), c.getFaculty() == null ? null : c.getFaculty().getId(),
                c.getRoomLabel(), c.getRoom() == null ? null : c.getRoom().getId(),
                c.getDayOfWeek(),
                c.getStartTime() == null ? null : com.college.timetable.util.TimeText.format(c.getStartTime()),
                c.getEndTime() == null ? null : com.college.timetable.util.TimeText.format(c.getEndTime()),
                c.getEntryType() == null ? null : c.getEntryType().name(),
                c.getValidationErrors());
    }

    public static LocalTime unused(LocalTime time) {
        return time;
    }
}
