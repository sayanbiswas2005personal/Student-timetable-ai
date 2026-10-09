package com.college.timetable.entity;

/** Processing state of a PDF import job. */
public enum ImportStatus {
    /** Accepted and stored, work not started. */
    PENDING,
    /** PDF text extraction in progress. */
    EXTRACTING,
    /** Text extraction done, parsing rows. */
    PARSING,
    /** OCR service invoked because text extraction was insufficient. */
    OCR_PENDING,
    /** Extraction/parsing finished, candidates are waiting for administrator review. */
    AWAITING_REVIEW,
    /** An administrator approved the candidates; they now form a draft timetable. */
    APPROVED,
    /** The resulting timetable has been published. */
    PUBLISHED,
    FAILED,
    CANCELLED
}