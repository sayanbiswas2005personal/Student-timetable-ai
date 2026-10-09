package com.college.timetable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * The authoritative group a student belongs to for a given program and academic term.
 *
 * <p>The section is never derived from the registration number at lookup time.
 */
@Entity
@Table(name = "sections",
        uniqueConstraints = @UniqueConstraint(name = "uk_sections_program_term_name",
                columnNames = {"program_id", "academic_term_id", "section_name"}))
public class Section extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_sections_program"))
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_term_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_sections_academic_term"))
    private AcademicTerm academicTerm;

    @Column(name = "section_name", nullable = false, length = 20)
    private String sectionName;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Program getProgram() {
        return program;
    }

    public void setProgram(Program program) {
        this.program = program;
    }

    public AcademicTerm getAcademicTerm() {
        return academicTerm;
    }

    public void setAcademicTerm(AcademicTerm academicTerm) {
        this.academicTerm = academicTerm;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}