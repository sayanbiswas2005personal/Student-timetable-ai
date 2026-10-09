package com.college.timetable.service.pdf;

import java.util.List;

/**
 * Extracted content of a single PDF page, together with why it may be unusable.
 *
 * @param pageNumber 1 based page number, exactly as an administrator would see it
 * @param text       plain text of the page, preserved for review
 * @param words      words with positions, used by the grid parser
 * @param sufficient true when the page carries enough characters to attempt parsing
 * @param reason     why a page was judged insufficient, null when it was fine
 */
public record PageText(int pageNumber,
                       String text,
                       List<TextWord> words,
                       boolean sufficient,
                       String reason) {

    public int characterCount() {
        return text == null ? 0 : text.strip().length();
    }
}