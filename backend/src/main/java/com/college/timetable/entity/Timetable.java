package com.college.timetable.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A versioned weekly timetable for one section.
 *
 * <p>Publishing creates a new version rather than mutating an existing one, so historical lookups
 * against a past date keep returning the timetable that was in force on that date.
 */
@Entity
@Table(name = "timetables",
        uniqueConstraints = @UniqueConstraint(name = "uk_timetables_section_version",
                columnNames = {"section_id", "version"}),
        indexes = @jakarta.persistence.Index(name = "ix_timetables_lookup",
                columnList = "section_id, status, effective_from, effective_to"))
public class Timetable extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_timetables_section"))
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_term_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_timetables_academic_term"))
    private AcademicTerm academicTerm;

    /** First date on which this version is in force (inclusive). */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Last date on which this version is in force (inclusive). Null means open ended. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TimetableStatus status = TimetableStatus.DRAFT;

    @Column(name = "source_filename", length = 255)
    private String sourceFilename;

    /** Monotonically increasing per section. */
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private Long publishedBy;

    public Section getSection() {
        return section;
    }

    public void setSection(Section section) {
        this.section = section;
    }

    public AcademicTerm getAcademicTerm() {
        return academicTerm;
    }

    public void setAcademicTerm(AcademicTerm academicTerm) {
        this.academicTerm = academicTerm;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public TimetableStatus getStatus() {
        return status;
    }

    public void setStatus(TimetableStatus status) {
        this.status = status;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Long getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(Long publishedBy) {
        this.publishedBy = publishedBy;
    }

    /** True when this version is in force on {@code date} (inclusive bounds). */
    public boolean isEffectiveOn(LocalDate date) {
        if (effectiveFrom == null || date == null) {
            return false;
        }
        if (date.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !date.isAfter(effectiveTo);
    }
}