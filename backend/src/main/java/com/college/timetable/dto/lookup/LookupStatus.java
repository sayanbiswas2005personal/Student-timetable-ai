package com.college.timetable.dto.lookup;

/** Outcome of a timetable lookup. Every value maps to a documented, user visible state. */
public enum LookupStatus {

    /** A published timetable exists and a CLASS entry contains the requested instant. */
    CLASS_IN_PROGRESS,

    /** A published timetable exists but nothing is scheduled at the requested instant. */
    NO_CLASS_NOW,

    /** A published timetable exists and an explicitly declared BREAK entry contains the instant. */
    BREAK,

    /** No timetable row exists at all for this section. */
    NO_TIMETABLE,

    /** Timetable rows exist but none of them are in PUBLISHED state for the requested date. */
    TIMETABLE_NOT_PUBLISHED,

    /** No student matches the supplied registration number. */
    STUDENT_NOT_FOUND,

    /** The search text matched more than one verified section or programme. */
    AMBIGUOUS_SEARCH,

    /** Two or more entries in the effective timetable claim the same instant: a data quality fault. */
    TIMETABLE_CONFLICT,

    /** The student record exists but has been deactivated. */
    STUDENT_INACTIVE,

    /**
     * Extension: the effective timetable declares an explicit {@code OTHER} block at this instant,
     * for example a library slot or an assembly. Reported instead of claiming "no class".
     */
    OTHER_IN_PROGRESS
}