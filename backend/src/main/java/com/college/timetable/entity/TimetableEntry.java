package com.college.timetable.entity;

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
 * One scheduled period inside a {@link Timetable}.
 *
 * <p>The interval is half open: {@code [startTime, endTime)}. A class from 10:00 to 11:00 is in
 * progress at 10:30 and over at 11:00.
 */
@Entity
@Table(name = "timetable_entries",
        indexes = {
                @Index(name = "ix_entries_timetable_day", columnList = "timetable_id, day_of_week, start_time"),
                @Index(name = "ix_entries_section_day", columnList = "section_id, day_of_week")
        })
public class TimetableEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timetable_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_entries_timetable"))
    private Timetable timetable;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_entries_section"))
    private Section section;

    /** Null when the source timetable does not resolve to a known subject. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id",
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_entries_subject"))
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id",
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_entries_faculty"))
    private Faculty faculty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id",
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_entries_room"))
    private Room room;

    /** 1 = Monday .. 7 = Sunday, matching {@link java.time.DayOfWeek#getValue()}. */
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private EntryType entryType = EntryType.CLASS;

    /** Verbatim text of the source cell, kept for review and debugging. */
    @Column(name = "raw_source_text", length = 1000)
    private String rawSourceText;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Column(name = "source_page_number")
    private Integer sourcePageNumber;

    public Timetable getTimetable() {
        return timetable;
    }

    public void setTimetable(Timetable timetable) {
        this.timetable = timetable;
    }

    public Section getSection() {
        return section;
    }

    public void setSection(Section section) {
        this.section = section;
    }

    public Subject getSubject() {
        return subject;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    public Faculty getFaculty() {
        return faculty;
    }

    public void setFaculty(Faculty faculty) {
        this.faculty = faculty;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(int dayOfWeek) {
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

    public String getRawSourceText() {
        return rawSourceText;
    }

    public void setRawSourceText(String rawSourceText) {
        this.rawSourceText = rawSourceText;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public Integer getSourcePageNumber() {
        return sourcePageNumber;
    }

    public void setSourcePageNumber(Integer sourcePageNumber) {
        this.sourcePageNumber = sourcePageNumber;
    }

    /**
     * Half open containment: {@code startTime <= instant < endTime}.
     */
    public boolean contains(LocalTime instant) {
        return !instant.isBefore(startTime) && instant.isBefore(endTime);
    }

    /**
     * True when this entry and {@code other} share at least one instant on the same day.
     */
    public boolean overlaps(TimetableEntry other) {
        if (other == null || other.getDayOfWeek() != dayOfWeek) {
            return false;
        }
        return startTime.isBefore(other.getEndTime()) && other.getStartTime().isBefore(endTime);
    }
}