package com.college.timetable.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a timetable section heading such as {@code "B.TECH CSE III A"} into its parts.
 *
 * <p>Colleges print the programme, then a semester in Roman numerals, then the section letter,
 * sometimes with a qualifier: {@code "B.TECH CSE V G (AIML)"}, {@code "BCA V BFSI"}. Nothing here
 * guesses: if the Roman numeral is missing the whole parse fails and the caller is expected to ask
 * a human rather than invent a semester.
 */
public final class SectionLabelParser {

    // Alternatives are ordered longest first: the regex engine takes the first one that matches,
    // so "III" must be tried before "II" and "XII" before "XI", otherwise the shorter numeral wins
    // and the leftover characters leak into the section name.
    private static final Pattern LABEL = Pattern.compile(
            "^(?<program>.+?)\\s+(?<semester>XII|VIII|VII|III|VI|IX|XI|IV|II|V|X|I)\\s*(?<section>.*)$");

    private SectionLabelParser() {
    }

    /**
     * @param sectionLabel the text printed above the grid, null when the page had none
     * @return the parsed parts, or null when the label cannot be read with confidence
     */
    public static ParsedSection parse(String sectionLabel) {
        if (sectionLabel == null || sectionLabel.isBlank()) {
            return null;
        }
        String label = sectionLabel.replaceAll("\\s+", " ").strip();

        Matcher matcher = LABEL.matcher(label);
        if (!matcher.matches()) {
            return null;
        }

        Integer semester = toSemester(matcher.group("semester"));
        if (semester == null || semester < 1 || semester > 12) {
            return null;
        }

        String program = matcher.group("program").replaceAll("\\s+", " ").strip();
        String section = matcher.group("section").replaceAll("\\s+", " ").strip();

        if (program.isBlank()) {
            return null;
        }
        // A programme with a single section prints no letter at all, for example "M.TECH DSDT I".
        // MAIN is a neutral, explicit stand-in rather than a silent guess at a letter.
        return new ParsedSection(label, program, semester,
                section.isBlank() ? "MAIN" : section.toUpperCase(Locale.ROOT));
    }

    public record ParsedSection(String rawLabel, String program, int semester, String section) {
    }

    static Integer toSemester(String roman) {
        switch (roman.toUpperCase(Locale.ROOT)) {
            case "I":
                return 1;
            case "II":
                return 2;
            case "III":
                return 3;
            case "IV":
                return 4;
            case "V":
                return 5;
            case "VI":
                return 6;
            case "VII":
                return 7;
            case "VIII":
                return 8;
            case "IX":
                return 9;
            case "X":
                return 10;
            case "XI":
                return 11;
            case "XII":
                return 12;
            default:
                return null;
        }
    }

    /** A stable code derived from the printed programme text, for example "B.TECH CSE" -> "BTCSE". */
    public static String programCode(String program) {
        return program.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}