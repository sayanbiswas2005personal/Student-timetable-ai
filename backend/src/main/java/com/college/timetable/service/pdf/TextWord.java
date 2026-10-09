package com.college.timetable.service.pdf;

import java.util.List;

/**
 * One word with its position on the page.
 *
 * <p>Timetable grids are read from geometry rather than from plain text order, because PDF text
 * extraction frequently interleaves cells in a way that loses the row and column structure.
 */
public record TextWord(String text, float x, float y, float width, float height) {

    /** Right edge, used to decide which column a word belongs to. */
    public float right() {
        return x + width;
    }

    public float centerX() {
        return x + width / 2f;
    }

    public float centerY() {
        return y + height / 2f;
    }

    public static List<TextWord> of(String... texts) {
        return java.util.Arrays.stream(texts).map(text -> new TextWord(text, 0f, 0f, 0f, 0f)).toList();
    }
}