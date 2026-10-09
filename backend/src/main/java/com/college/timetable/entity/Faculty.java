package com.college.timetable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Teaching staff. Entirely optional: many published timetables do not name the faculty, and the
 * system must work without it.
 */
@Entity
@Table(name = "faculties",
        uniqueConstraints = @UniqueConstraint(name = "uk_faculties_code", columnNames = {"faculty_code"}))
public class Faculty extends BaseEntity {

    @Column(name = "faculty_name", nullable = false, length = 150)
    private String facultyName;

    @Column(name = "faculty_code", length = 40)
    private String facultyCode;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String getFacultyName() {
        return facultyName;
    }

    public void setFacultyName(String facultyName) {
        this.facultyName = facultyName;
    }

    public String getFacultyCode() {
        return facultyCode;
    }

    public void setFacultyCode(String facultyCode) {
        this.facultyCode = facultyCode;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}