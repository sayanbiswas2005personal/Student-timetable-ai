package com.college.timetable.entity;

/** What a timetable row represents. */
public enum EntryType {
    /** A scheduled teaching period. */
    CLASS,
    /** An explicitly declared break / recess in the source timetable. */
    BREAK,
    /** Anything else declared in the source that is neither a class nor a break. */
    OTHER
}