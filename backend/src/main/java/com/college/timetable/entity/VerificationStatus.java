package com.college.timetable.entity;

/** Confidence level of a single imported timetable row, set by the administrator during review. */
public enum VerificationStatus {
    /** Machine-extracted, not yet looked at by a human. */
    UNVERIFIED,
    /** A human checked the row and accepted it. */
    VERIFIED,
    /** A human corrected the row. */
    CORRECTED,
    /** A human rejected the row; it will not be imported. */
    REJECTED
}