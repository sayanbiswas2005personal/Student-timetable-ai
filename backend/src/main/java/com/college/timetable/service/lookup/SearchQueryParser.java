package com.college.timetable.service.lookup;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic free text reader for search boxes.
 *
 * <p>It does not guess. Every value it returns was written literally in the input, and anything it
 * is not sure about is reported as absent so the caller can ask the user instead of picking.
 */
public final class SearchQueryParser {

    /**
     * "semester 5", "sem 5", "5th semester", "fifth semester".
     *
     * <p>Three alternatives, each self contained so a match can be attributed to the right capture
     * group: a number after the word, an ordinal before it, or a number word before it.
     */
    private static final Pattern SEMESTER = Pattern.compile(
            "\\bsem(?:ester)?\\.?\\s*(?<n1>\\d{1,2})\\b"
                    + "|(?<ord1>\\d{1,2})(?:st|nd|rd|th)?\\s*\\bsem(?:ester)?\\b"
                    + "|\\b(?<word>first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth"
                    + "|eleventh|twelfth)\\s*\\bsem(?:ester)?\\b",
            Pattern.CASE_INSENSITIVE);

    /** "section D", "sec-d", "section: D". A bare letter is deliberately not matched. */
    private static final Pattern SECTION = Pattern.compile(
            "\\bsec(?:tion)?\\.?\\s*[-:#]?\\s*\"?\\s*(?<name>[A-Za-z0-9][A-Za-z0-9\\-]{0,9})\\b",
            Pattern.CASE_INSENSITIVE);

    /** "2025", "2025-26", "2025/26". */
    private static final Pattern ACADEMIC_YEAR = Pattern.compile("\\b(?<y>20\\d{2})(?:\\s*[-/]\\s*(?<y2>\\d{2,4}))?\\b");

    /** Registration numbers contain slashes and at least two digit runs. */
    private static final Pattern REGISTRATION =
            Pattern.compile("^[A-Za-z0-9]{1,12}(?:\\s*/\\s*[A-Za-z0-9]{1,12}){2,}$");

    private static final String[] NUMBER_WORDS = {
            "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth",
            "eleventh", "twelfth"
    };

    private SearchQueryParser() {
    }

    /**
     * @param freeText       what the user typed
     * @param programHint    text left after the recognised keywords have been removed
     * @param registrationRaw registration number typed alongside a course description, may be null
     */
    public record ParsedQuery(String raw,
                              Integer semesterNumber,
                              String academicYear,
                              String sectionLabel,
                              String registrationNumber,
                              String programHint) {
    }

    public static ParsedQuery parse(String freeText, String registrationRaw) {
        String text = freeText == null ? "" : freeText.trim();
        String work = text;

        Integer semester = null;
        Matcher semMatcher = SEMESTER.matcher(work);
        if (semMatcher.find()) {
            String digits = semMatcher.group("n1");
            if (digits == null) {
                digits = semMatcher.group("ord1");
            }
            if (digits != null) {
                semester = parseInt(digits);
            } else if (semMatcher.group("word") != null) {
                for (int i = 0; i < NUMBER_WORDS.length; i++) {
                    if (NUMBER_WORDS[i].equalsIgnoreCase(semMatcher.group("word"))) {
                        semester = i + 1;
                        break;
                    }
                }
            }
            work = removeMatched(work, semMatcher);
        }

        String academicYear = null;
        Matcher yearMatcher = ACADEMIC_YEAR.matcher(work);
        if (yearMatcher.find()) {
            academicYear = normalizeYear(yearMatcher);
            work = removeMatched(work, yearMatcher);
        }

        String section = null;
        Matcher sectionMatcher = SECTION.matcher(work);
        if (sectionMatcher.find()) {
            section = sectionMatcher.group("name");
            work = removeMatched(work, sectionMatcher);
        }

        String registration = registrationRaw;
        if (registration == null && REGISTRATION.matcher(text.replaceAll("\\s+", "")).matches()) {
            registration = text.replaceAll("\\s+", "");
        }

        return new ParsedQuery(text, semester, academicYear, section, registration, work.trim());
    }

    private static String normalizeYear(Matcher matcher) {
        String y1 = matcher.group("y");
        String y2 = matcher.group("y2");
        if (y2 == null) {
            return y1;
        }
        if (y2.length() == 4) {
            return y1 + "-" + y2;
        }
        String tail = String.format("%02d", Integer.parseInt(y2) % 100);
        return y1 + "-" + tail;
    }

    private static String removeMatched(String input, Matcher matcher) {
        return input.substring(0, matcher.start()) + " " + input.substring(matcher.end());
    }

    private static Integer parseInt(String digits) {
        try {
            int value = Integer.parseInt(digits);
            return value >= 1 && value <= 12 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}