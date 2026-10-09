package com.college.timetable.util;

import java.util.Locale;

/**
 * Reduces text to a comparable form for deterministic matching.
 *
 * <p>Case is folded, punctuation is treated as a separator, and runs of whitespace collapse to a
 * single space. "B.Tech CSE AI-ML", "b tech cse ai ml" and "BTech-CSE-AIML" all normalise to the
 * same value, so a user's spelling cannot change which programme is matched.
 */
public final class SearchText {

    private SearchText() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        boolean pendingSpace = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (pendingSpace && sb.length() > 0) {
                    sb.append(' ');
                }
                pendingSpace = false;
                sb.append(c);
            } else {
                pendingSpace = true;
            }
        }
        return sb.toString();
    }

    /** Like {@link #normalize(String)} but also drops spaces, for codes such as BTCSEAIML. */
    public static String compact(String text) {
        return normalize(text).replace(" ", "");
    }

    public static boolean containsWord(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) {
            return false;
        }
        return (" " + haystack + " ").contains(" " + needle + " ");
    }
}