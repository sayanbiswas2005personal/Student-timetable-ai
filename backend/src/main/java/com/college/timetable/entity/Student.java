package com.college.timetable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A student, linked to exactly one authoritative section.
 *
 * <p>{@code registrationNumber} keeps the original displayed value. {@code registrationNumberNormalized}
 * is the search key: upper-cased and stripped of surrounding whitespace. Lookups always compare
 * against the normalized column so that spacing and case differences are harmless while still
 * producing an exact match only.
 */
@Entity
@Table(name = "students",
        uniqueConstraints = @UniqueConstraint(name = "uk_students_registration_normalized",
                columnNames = {"registration_number_normalized"}),
        indexes = @Index(name = "ix_students_section", columnList = "section_id"))
public class Student extends BaseEntity {

    @Column(name = "registration_number", nullable = false, length = 40)
    private String registrationNumber;

    @Column(name = "registration_number_normalized", nullable = false, length = 40)
    private String registrationNumberNormalized;

    /** Optional. Only shown when the college records it and the viewer is allowed to see it. */
    @Column(name = "full_name", length = 150)
    private String fullName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_students_section"))
    private Section section;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getRegistrationNumberNormalized() {
        return registrationNumberNormalized;
    }

    public void setRegistrationNumberNormalized(String registrationNumberNormalized) {
        this.registrationNumberNormalized = registrationNumberNormalized;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Section getSection() {
        return section;
    }

    public void setSection(Section section) {
        this.section = section;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}