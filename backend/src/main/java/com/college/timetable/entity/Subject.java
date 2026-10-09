package com.college.timetable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A subject. {@code programId} is optional because the same subject is often shared across
 * programmes (for example a common mathematics course).
 */
@Entity
@Table(name = "subjects",
        uniqueConstraints = @UniqueConstraint(name = "uk_subjects_code", columnNames = {"subject_code"}))
public class Subject extends BaseEntity {

    @Column(name = "subject_code", nullable = false, length = 40)
    private String subjectCode;

    @Column(name = "subject_name", nullable = false, length = 200)
    private String subjectName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id",
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_subjects_program"))
    private Program program;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public Program getProgram() {
        return program;
    }

    public void setProgram(Program program) {
        this.program = program;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}