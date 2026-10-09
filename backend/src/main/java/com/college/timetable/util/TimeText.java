package com.college.timetable.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lenient reader and strict writer for the time formats that appear in college timetables.
 *
 * <p>Reading is tolerant because source documents vary ("9:30", "09:30", "9.30", "0930",
 * "9:30 AM"). Writing is always {@code HH:mm} so the API has one shape.
 */
public final class TimeText {

    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private static final Pattern TIME = Pattern.compile(
            "^\\s*(?<h>\\d{1,2})\\s*[:.h]?\\s*(?<m>\\d{2})?\\s*(?<ampm>am|pm|a\\.?m\\.?|p\\.?m\\.?)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private TimeText() {
    }

    /** Returns null when the text cannot be read as a wall clock time. */
    public static LocalTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = TIME.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        try {
            int hour = Integer.parseInt(matcher.group("h"));
            String minuteGroup = matcher.group("m");
            int minute = minuteGroup == null || minuteGroup.isEmpty() ? 0 : Integer.parseInt(minuteGroup);
            if (minute < 0 || minute > 59 || hour > 23) {
                return null;
            }
            String ampm = matcher.group("ampm");
            if (ampm != null) {
                if (hour < 1 || hour > 12) {
                    return null;
                }
                boolean isPm = ampm.toLowerCase(Locale.ROOT).startsWith("p");
                if (isPm && hour < 12) {
                    hour += 12;
                } else if (!isPm && hour == 12) {
                    hour = 0;
                }
            }
            return LocalTime.of(hour, minute);
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            return null;
        }
    }

    /** Returns the raw text when it is not a valid time, otherwise a normalised form. */
    public static String parseToTextOrKeep(String text) {
        LocalTime parsed = parse(text);
        return parsed == null ? (text == null ? null : text.trim()) : format(parsed);
    }

    public static String format(LocalTime time) {
        return time == null ? null : time.format(OUT);
    }

    /** Parses a time that must be present, for command line tooling such as the user seeder. */
    public static LocalTime parseRequired(String text, String context) {
        LocalTime parsed = parse(text);
        if (parsed == null) {
            throw new IllegalArgumentException("Cannot read '" + text + "' as a time (" + context + ")");
        }
        return parsed;
    }

    /** True when the text parses as a time. */
    public static boolean isValid(String text) {
        try {
            LocalTime parsed = parse(text);
            return parsed != null;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }
}