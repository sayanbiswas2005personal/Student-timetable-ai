package com.college.timetable.entity;

/** Review state of a single extracted row. */
public enum ReviewStatus {
    /** Extracted, waiting for a human. */
    PENDING,
    /** A human confirmed the row as extracted. */
    APPROVED,
    /** A human edited the row; the edited values are authoritative. */
    EDITED,
    /** A human rejected the row; it is excluded from the resulting timetable. */
    REJECTED
}