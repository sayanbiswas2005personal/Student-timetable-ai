package com.college.timetable.entity;

/** Status lifecycle of a {@link Timetable}. */
public enum TimetableStatus {
    /** Being edited. Never used by the lookup engine. */
    DRAFT,
    /** Populated from an import job and waiting for an administrator to review it. */
    UNDER_REVIEW,
    /** Approved data. The only status the lookup engine will read. */
    PUBLISHED,
    /** Superseded by a newer published version, kept for historical lookup and rollback. */
    SUPERSEDED
}