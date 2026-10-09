package com.college.timetable.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.college.timetable.entity.Timetable;

/** Helpers shared by the lookup engine and the timetable services. */
public final class TimetableDates {

    private TimetableDates() {
    }

    /** True when the term's own date range covers {@code date}. */
    public static boolean termCovers(Timetable timetable, LocalDate date) {
        var term = timetable.getAcademicTerm();
        if (term == null || term.getStartDate() == null || term.getEndDate() == null) {
            return false;
        }
        return !date.isBefore(term.getStartDate()) && !date.isAfter(term.getEndDate());
    }

    public static ZonedDateTime startOfDay(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone);
    }
}