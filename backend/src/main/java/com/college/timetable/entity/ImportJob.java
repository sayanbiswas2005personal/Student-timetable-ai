package com.college.timetable.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "import_jobs", indexes = @Index(name = "ix_import_jobs_status", columnList = "status"))
public class ImportJob extends BaseEntity {

    /** Original filename as supplied by the browser. Never used as a path. */
    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    /** Generated internal filename. This is the only name used to build filesystem paths. */
    @Column(name = "stored_filename", nullable = false, length = 255)
    private String storedFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ImportStatus status = ImportStatus.PENDING;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "uploaded_by_username", length = 60)
    private String uploadedByUsername;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_summary", length = 2000)
    private String errorSummary;

    @Column(name = "total_pages")
    private Integer totalPages;

    /** Pages whose text extraction produced too little usable text to parse. */
    @Column(name = "pages_needing_ocr")
    private Integer pagesNeedingOcr;

    @Column(name = "ocr_service_used", nullable = false)
    private boolean ocrServiceUsed = false;

    /** Timable created when the import was approved. */
    @Column(name = "result_timetable_id")
    private Long resultTimetableId;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public void setStoredFilename(String storedFilename) {
        this.storedFilename = storedFilename;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public void setStatus(ImportStatus status) {
        this.status = status;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(Long uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getUploadedByUsername() {
        return uploadedByUsername;
    }

    public void setUploadedByUsername(String uploadedByUsername) {
        this.uploadedByUsername = uploadedByUsername;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }

    public Integer getPagesNeedingOcr() {
        return pagesNeedingOcr;
    }

    public void setPagesNeedingOcr(Integer pagesNeedingOcr) {
        this.pagesNeedingOcr = pagesNeedingOcr;
    }

    public boolean isOcrServiceUsed() {
        return ocrServiceUsed;
    }

    public void setOcrServiceUsed(boolean ocrServiceUsed) {
        this.ocrServiceUsed = ocrServiceUsed;
    }

    public Long getResultTimetableId() {
        return resultTimetableId;
    }

    public void setResultTimetableId(Long resultTimetableId) {
        this.resultTimetableId = resultTimetableId;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }
}