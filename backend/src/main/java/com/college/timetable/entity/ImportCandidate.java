package com.college.timetable.entity;

import java.math.BigDecimal;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One timetable row proposed by the PDF parser, before an administrator has approved it.
 *
 * <p>Nothing here is trusted. The parser's output lives in {@link #rawText} and
 * {@link #extractedFields} for inspection, while the resolved columns below are what the
 * administrator edited and approved.
 */
@Entity
@Table(name = "import_candidates",
        indexes = @Index(name = "ix_candidates_job", columnList = "import_job_id, page_number"))
public class ImportCandidate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_job_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_candidates_job"))
    private ImportJob importJob;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    /** Verbatim text of the source cell. */
    @Column(name = "raw_text", length = 2000)
    private String rawText;

    /** JSON object describing every field the parser found, including ones it could not resolve. */
    @Column(name = "extracted_fields", columnDefinition = "TEXT")
    private String extractedFields;

    /** Parser confidence 0..1, null when the parser cannot estimate one. */
    @Column(name = "confidence")
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Column(name = "reviewer_notes", length = 1000)
    private String reviewerNotes;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    // ---- resolved fields, maintained by the reviewer ----

    @Column(name = "section_label", length = 120)
    private String sectionLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id",
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_candidates_section"))
    private Section section;

    @Column(name = "subject_label", length = 300)
    private String subjectLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id",
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_candidates_subject"))
    private Subject subject;

    @Column(name = "faculty_label", length = 300)
    private String facultyLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id",
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_candidates_faculty"))
    private Faculty faculty;

    @Column(name = "room_label", length = 120)
    private String roomLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id",
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_candidates_room"))
    private Room room;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", length = 20)
    private EntryType entryType;

    @Column(name = "validation_errors", length = 1000)
    private String validationErrors;

    public ImportJob getImportJob() {
        return importJob;
    }

    public void setImportJob(ImportJob importJob) {
        this.importJob = importJob;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getExtractedFields() {
        return extractedFields;
    }

    public void setExtractedFields(String extractedFields) {
        this.extractedFields = extractedFields;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(ReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewerNotes() {
        return reviewerNotes;
    }

    public void setReviewerNotes(String reviewerNotes) {
        this.reviewerNotes = reviewerNotes;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getSectionLabel() {
        return sectionLabel;
    }

    public void setSectionLabel(String sectionLabel) {
        this.sectionLabel = sectionLabel;
    }

    public Section getSection() {
        return section;
    }

    public void setSection(Section section) {
        this.section = section;
    }

    public String getSubjectLabel() {
        return subjectLabel;
    }

    public void setSubjectLabel(String subjectLabel) {
        this.subjectLabel = subjectLabel;
    }

    public Subject getSubject() {
        return subject;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    public String getFacultyLabel() {
        return facultyLabel;
    }

    public void setFacultyLabel(String facultyLabel) {
        this.facultyLabel = facultyLabel;
    }

    public Faculty getFaculty() {
        return faculty;
    }

    public void setFaculty(Faculty faculty) {
        this.faculty = faculty;
    }

    public String getRoomLabel() {
        return roomLabel;
    }

    public void setRoomLabel(String roomLabel) {
        this.roomLabel = roomLabel;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public String getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(String validationErrors) {
        this.validationErrors = validationErrors;
    }
}