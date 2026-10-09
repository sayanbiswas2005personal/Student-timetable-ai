package com.college.timetable.entity;

/** Application role. Public self-registration does not exist in this system. */
public enum Role {
    /** Can look up students and timetables. */
    STAFF,
    /** Everything STAFF can do, plus data management and timetable publishing. */
    ADMIN
}