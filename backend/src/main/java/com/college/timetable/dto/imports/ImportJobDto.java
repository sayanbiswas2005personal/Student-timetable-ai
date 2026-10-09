package com.college.timetable.dto.imports;

import java.time.Instant;

import com.college.timetable.entity.ImportJob;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ImportJob", description = "State of one uploaded timetable PDF")
public record ImportJobDto(
        Long id,
        String filename,
        String status,
        Integer totalPages,
        Integer pagesNeedingOcr,
        boolean ocrServiceUsed,
        Long uploadedBy,
        String uploadedByUsername,
        Instant startedAt,
        Instant completedAt,
        String errorSummary,
        Long resultTimetableId,
        long candidateCount,
        long pendingCount,
        Instant createdAt) {

    public static ImportJobDto from(ImportJob job, long candidateCount, long pendingCount) {
        return new ImportJobDto(job.getId(), job.getFilename(), job.getStatus().name(),
                job.getTotalPages(), job.getPagesNeedingOcr(), job.isOcrServiceUsed(),
                job.getUploadedBy(), job.getUploadedByUsername(),
                job.getStartedAt(), job.getCompletedAt(), job.getErrorSummary(),
                job.getResultTimetableId(), candidateCount, pendingCount, job.getCreatedAt());
    }
}
