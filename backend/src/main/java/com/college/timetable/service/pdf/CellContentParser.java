package com.college.timetable.service.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits one timetable cell into subject, faculty and room.
 *
 * <p>Cells in practice read like {@code "Cloud Computing (CSE11036) Prof. A Sen (56312) AU6-4304"}:
 * a room printed on its own line, then a subject with its code in brackets, then a faculty name
 * with a staff code. This class follows that order but never assumes it: every part it cannot
 * recognise is reported in {@code unresolved} and the confidence is kept low, because nothing
 * from here may reach a published timetable without a human approving it.
 */
public class CellContentParser {

    /**
     * Subject codes seen in real timetables: {@code CSE11036}, {@code MGT11402}, {@code LT-3},
     * and prefixed variants such as {@code MINOR_CSE14050}.
     */
    private static final Pattern SUBJECT_CODE = Pattern.compile(
            "(?<![A-Za-z0-9])([A-Z][A-Z0-9]*_[A-Z]{2,8}\\d{3,6}[A-Z0-9]*"
                    + "|[A-Z]{2,8}[\\s_-]?\\d{3,6}[A-Z0-9]*)");

    /** Staff codes printed after a faculty name: "(56312)". */
    private static final Pattern FACULTY_CODE = Pattern.compile("\\(?(?<code>\\d{4,6})\\)?");

    /**
     * Room codes, compound form first.
     *
     * <p>Colleges print these as block, then use, then number: "AU6-LAB-4001A", "AU6-4305". A
     * pattern that only accepts a single dash would keep "LAB-4001" and silently drop the block,
     * producing a room that does not exist, so the compound form is matched first.
     */
    private static final Pattern ROOM = Pattern.compile(
            "\\b([A-Z]{1,4}\\d{0,3}[\\s-][A-Z]{2,6}[\\s-]\\d{3,5}[A-Z]?)"
                    + "|\\b([A-Z]{1,4}\\d{0,3}[\\s-]\\d{2,5}[A-Z]?)\\b");

    private static final Pattern HONORIFIC = Pattern.compile(
            "^(?:Dr\\.?|Prof\\.?|Mr\\.?|Mrs\\.?|Ms\\.?|Msr\\.?|Sri\\.?)\\s+", Pattern.CASE_INSENSITIVE);

    private static final Pattern BREAK_HINT = Pattern.compile(
            "\\b(break|lunch|recess|tea|free\\s*period|no\\s*class|maintenance|assembly)\\b",
            Pattern.CASE_INSENSITIVE);

    public record ParsedCell(String subjectName,
                             String subjectCode,
                             String facultyName,
                             String facultyCode,
                             String roomCode,
                             EntryTypeHint entryType,
                             double confidence,
                             List<String> unresolved) {
    }

    /** Coarse entry type suggestion. The reviewer decides the final value. */
    public enum EntryTypeHint {
        CLASS, BREAK, OTHER
    }

    public ParsedCell parse(String rawText) {
        String text = collapse(rawText);
        List<String> unresolved = new ArrayList<>();

        if (text.isBlank()) {
            return new ParsedCell(null, null, null, null, null, EntryTypeHint.OTHER, 0d,
                    List.of("The cell is empty."));
        }

        EntryTypeHint entryType = BREAK_HINT.matcher(text).find()
                ? EntryTypeHint.BREAK : EntryTypeHint.CLASS;

        // 1. Room codes usually sit on their own line at the top or bottom of the cell.
        String roomCode = null;
        Matcher roomMatcher = ROOM.matcher(text);
        if (roomMatcher.find()) {
            String matched = roomMatcher.group(1) != null ? roomMatcher.group(1) : roomMatcher.group(2);
            roomCode = matched.replaceAll("\\s+", "-").toUpperCase(java.util.Locale.ROOT);
            text = remove(text, roomMatcher.start(), roomMatcher.end());
        }

        // 2. Subject code. Everything before it is the subject name.
        String subjectCode = null;
        String subjectName = null;
        String afterSubject = text;
        Matcher subjectMatcher = SUBJECT_CODE.matcher(text);
        if (subjectMatcher.find()) {
            subjectCode = subjectMatcher.group(1).replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
            subjectName = tidy(text.substring(0, subjectMatcher.start()));
            afterSubject = text.substring(subjectMatcher.end());
        } else {
            subjectName = tidy(text);
        }
        if (subjectName != null && subjectName.isBlank()) {
            subjectName = null;
        }

        // 3. Faculty: a staff code after the subject, with the name in front of it.
        String facultyCode = null;
        String facultyName = null;
        Matcher staffMatcher = FACULTY_CODE.matcher(afterSubject);
        if (staffMatcher.find()) {
            facultyCode = staffMatcher.group("code");
            facultyName = tidy(stripHonorifics(afterSubject.substring(0, staffMatcher.start())));
        }

        double confidence = 0d;
        if (subjectCode != null) {
            confidence += 0.5;
        }
        if (subjectName != null) {
            confidence += 0.25;
        }
        if (entryType == EntryTypeHint.BREAK) {
            confidence += 0.15;
        }
        if (facultyName != null || facultyCode != null) {
            confidence += 0.05;
        }
        if (roomCode != null) {
            confidence += 0.05;
        }

        if (subjectName == null && subjectCode == null) {
            unresolved.add("No subject could be recognised in this cell.");
            confidence = Math.min(confidence, 0.2);
        }
        if (entryType == EntryTypeHint.CLASS && roomCode == null) {
            unresolved.add("No room code was recognised.");
        }

        return new ParsedCell(subjectName, subjectCode, facultyName, facultyCode, roomCode,
                entryType, Math.min(confidence, 1.0), List.copyOf(unresolved));
    }

    private static String remove(String text, int from, int to) {
        return collapse(text.substring(0, from) + " " + text.substring(to));
    }

    private static String collapse(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    private static String tidy(String text) {
        return text.replaceAll("\\(\\s*\\)", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\s,;:()\\-.]+|[\\s,;:()\\-.]+$", "")
                .strip();
    }

    private static String stripHonorifics(String text) {
        String result = text;
        boolean changed = true;
        while (changed) {
            String before = result;
            result = HONORIFIC.matcher(result).replaceFirst("");
            result = result.replaceAll("^[(]\\s*[A-Za-z]{1,6}\\s*[.)]\\s*", "");
            changed = !before.equals(result);
        }
        return tidy(result);
    }
}