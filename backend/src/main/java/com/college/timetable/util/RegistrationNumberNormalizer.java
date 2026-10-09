package com.college.timetable.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalises registration numbers so that harmless formatting differences still match, while
 * staying an <em>exact</em> comparison afterwards.
 *
 * <p>Only clearly cosmetic differences are removed:
 * <ul>
 *   <li>leading and trailing whitespace,</li>
 *   <li>non breaking and zero width space characters that some keyboards and PDFs insert,</li>
 *   <li>runs of internal whitespace, collapsed to a single space,</li>
 *   <li>letter case.</li>
 * </ul>
 *
 * <p>Punctuation is never altered, so "UG/02/A/2023/1" can never match "UG/02/A/2023/10".
 */
public final class RegistrationNumberNormalizer {

    /** Whitespace plus the invisible characters that appear in pasted or scanned text. */
    private static final Pattern COSMETIC_WHITESPACE = Pattern.compile("[\\s\\u00A0\\u200B\\uFEFF]+");

    private RegistrationNumberNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = COSMETIC_WHITESPACE.matcher(raw).replaceAll(" ").trim();
        return cleaned.toUpperCase(Locale.ROOT);
    }

    /** True when the input carries no characters at all after normalisation. */
    public static boolean isBlank(String raw) {
        String normalized = normalize(raw);
        return normalized == null || normalized.isEmpty();
    }
}