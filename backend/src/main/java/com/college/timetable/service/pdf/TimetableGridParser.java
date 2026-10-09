package com.college.timetable.service.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.college.timetable.util.TimeText;

/**
 * Reads timetable grids out of positioned PDF text.
 *
 * <p>The parser is deliberately structural rather than tied to one college's template. It finds:
 * <ol>
 *   <li>a header row that contains at least two time ranges, which gives the column boundaries;</li>
 *   <li>the day column, identified by the weekday abbreviations that appear to its left;</li>
 *   <li>each cell as the words whose x position falls inside one column and one row.</li>
 * </ol>
 *
 * <p>A different layout only needs a different way to find the column boundaries, which is why
 * {@link ColumnBoundaryDetector} is an interface rather than a hard coded rule.
 */
public class TimetableGridParser {

    /** "9:30 - 10:25", "09:30-10:25", "9.30 - 10.25". */
    private static final Pattern TIME_RANGE = Pattern.compile(
            "(?<a>\\d{1,2}\\s*[:.h]\\s*\\d{2})\\s*(?:-|–|—|to|TO)\\s*(?<b>\\d{1,2}\\s*[:.h]\\s*\\d{2})");

    private static final Map<String, Integer> DAYS = buildDayMap();

    private final ColumnBoundaryDetector boundaryDetector;

    public TimetableGridParser(ColumnBoundaryDetector boundaryDetector) {
        this.boundaryDetector = boundaryDetector;
    }

    /** One grid cell recovered from the page. */
    public record Cell(int dayOfWeek,
                       String dayLabel,
                       String startTime,
                       String endTime,
                       String rawText,
                       String columnLabel) {
    }

    /**
     * Everything a single page contributed.
     *
     * @param sectionLabel the heading printed above the grid, for example "B.TECH CSE III A", or
     *                     null when the page has no recognisable heading. A document with one
     *                     section per page is read correctly because each page carries its own.
     */
    public record PageParseResult(List<Cell> cells, List<String> warnings, String sectionLabel) {
    }

    public static String dayName(int dayOfWeek) {
        return DAYS.entrySet().stream()
                .filter(entry -> entry.getValue() == dayOfWeek)
                .map(Map.Entry::getKey)
                .findFirst()
                .map(name -> name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1))
                .orElse("Day " + dayOfWeek);
    }

    public static TimetableGridParser standard() {
        return new TimetableGridParser(new HeaderTimeSlotDetector());
    }

    public PageParseResult parse(PageText page) {
        if (!page.sufficient()) {
            return new PageParseResult(List.of(),
                    List.of("Page " + page.pageNumber() + " was skipped: " + page.reason()), null);
        }

        List<Row> rows = groupIntoRows(page.words());
        List<String> warnings = new ArrayList<>();

        Optional<Header> header = boundaryDetector.detect(rows);
        if (header.isEmpty()) {
            return new PageParseResult(List.of(),
                    List.of("Page " + page.pageNumber()
                            + " has no recognisable time-slot header, so no grid could be read."), null);
        }

        Header detected = header.get();
        String sectionLabel = detectSectionHeading(rows, detected.headerCenterY());

        // The day column sits to the left of the first time range. Cell text is often printed
        // further left than the header label, so the first slot has to start after the day column
        // rather than at the header text itself.
        float dayColumnRight = 0f;
        for (Row row : rows) {
            DayHit hit = dayLabel(row);
            if (hit != null) {
                dayColumnRight = Math.max(dayColumnRight, hit.rightEdge());
            }
        }

        List<TimeSlot> slots = withFirstSlotStartAt(detected.slots(), dayColumnRight + COLUMN_TOLERANCE);

        // A timetable cell is usually several visual lines tall, so text is accumulated per
        // (weekday, slot) instead of emitting one cell per line.
        //
        // Day attribution cannot simply follow the page downwards. In real college timetables the
        // weekday label is often centred vertically inside its own band rather than sitting at the
        // top, so a rule of "the last label seen above this row wins" would hand the upper half of
        // a day to the previous day. Instead each row goes to the weekday whose label is nearest to
        // it vertically, and rows outside the first and last labels belong to no day at all.
        List<DayHit> dayHits = new ArrayList<>();
        for (Row row : rows) {
            DayHit hit = dayLabel(row);
            if (hit != null) {
                dayHits.add(hit);
            }
        }
        dayHits.sort((a, b) -> Float.compare(a.rowY(), b.rowY()));

        Map<Integer, Map<Integer, StringBuilder>> grid = new LinkedHashMap<>();
        Map<Integer, String> dayLabels = new LinkedHashMap<>();

        if (!dayHits.isEmpty()) {
            for (Row row : rows) {
                if (!Float.isFinite(row.centerY())) {
                    continue;
                }
                // PDFBox reports y measured downwards from the top of the page, so the header and
                // anything above it have a smaller y than the body rows.
                if (row.centerY() <= detected.headerCenterY() + ROW_TOLERANCE) {
                    continue;
                }
                // Rows above the first label, or below the last, still belong to the nearest band:
                // a band often prints its heading above the weekday label, and dropping those lines
                // would lose the first cell of the day.
                DayHit owner = nearestDay(dayHits, row.centerY());
                Map<Integer, StringBuilder> dayRow = grid.computeIfAbsent(owner.dayOfWeek(),
                        key -> new LinkedHashMap<>());
                dayLabels.putIfAbsent(owner.dayOfWeek(), owner.label());

                for (int column = 0; column < slots.size(); column++) {
                    final TimeSlot columnSlot = slots.get(column);
                    List<TextWord> inColumn = row.words().stream()
                            .filter(word -> columnSlot.contains(word.centerX()))
                            .toList();
                    if (inColumn.isEmpty()) {
                        continue;
                    }
                    String fragment = String.join(" ", inColumn.stream().map(TextWord::text).toList()).strip();
                    if (fragment.isEmpty()) {
                        continue;
                    }
                    dayRow.computeIfAbsent(column, key -> new StringBuilder()).append(fragment).append(' ');
                }
            }
        }

        List<Cell> cells = new ArrayList<>();
        for (Map.Entry<Integer, Map<Integer, StringBuilder>> day : grid.entrySet()) {
            String label = dayLabels.getOrDefault(day.getKey(), "");
            for (Map.Entry<Integer, StringBuilder> column : day.getValue().entrySet()) {
                TimeSlot slot = slots.get(column.getKey());
                cells.add(new Cell(day.getKey(), label, slot.start(), slot.end(),
                        column.getValue().toString().strip(), slot.label()));
            }
        }

        if (cells.isEmpty()) {
            warnings.add("Page " + page.pageNumber() + " produced no timetable cells.");
        } else {
            warnings.addAll(unevenDayWarning(page.pageNumber(), cells));
        }
        return new PageParseResult(List.copyOf(cells), List.copyOf(warnings), sectionLabel);
    }


    private static List<TimeSlot> withFirstSlotStartAt(List<TimeSlot> slots, float left) {
        List<TimeSlot> adjusted = new ArrayList<>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            TimeSlot slot = slots.get(i);
            adjusted.add(i == 0 ? new TimeSlot(slot.label(), slot.start(), slot.end(), left, slot.right())
                    : slot);
        }
        return List.copyOf(adjusted);
    }

    // ------------------------------------------------------------- geometry

    record Row(float centerY, List<TextWord> words) {
    }

    record TimeSlot(String label, String start, String end, float left, float right) {
        boolean contains(float x) {
            return x >= left - COLUMN_TOLERANCE && x <= right + COLUMN_TOLERANCE;
        }
    }

    record Header(List<TimeSlot> slots, float headerCenterY) {
    }

    private static final float ROW_TOLERANCE = 4.0f;
    private static final float COLUMN_TOLERANCE = 6.0f;

    /** Groups words into visual rows using their vertical centre. */
    private static List<Row> groupIntoRows(List<TextWord> words) {
        // PDFBox reports y measured downwards from the top of the page, so ascending y is
        // top to bottom, which is the order a timetable is read in.
        List<TextWord> sorted = words.stream()
                .sorted((a, b) -> Float.compare(a.centerY(), b.centerY()))
                .toList();
        List<Row> rows = new ArrayList<>();
        List<TextWord> current = new ArrayList<>();
        float currentY = Float.NaN;

        for (TextWord word : sorted) {
            if (!current.isEmpty() && Math.abs(word.centerY() - currentY) <= ROW_TOLERANCE) {
                current.add(word);
            } else {
                if (!current.isEmpty()) {
                    rows.add(new Row(currentY, List.copyOf(current)));
                }
                current = new ArrayList<>();
                current.add(word);
                currentY = word.centerY();
            }
        }
        if (!current.isEmpty()) {
            rows.add(new Row(currentY, List.copyOf(current)));
        }
        return rows;
    }

    /** A weekday label found on a row, with the geometry needed to attribute other rows to it. */
    private record DayHit(int dayOfWeek, String label, float rightEdge, float rowY) {
    }

    /** The weekday whose label sits closest to this row, vertically. */
    /**
     * Reads the heading printed above the grid.
     *
     * <p>A section heading is set noticeably larger than everything around it: the timetable
     * heading here is roughly twice the height of the cell text, while the letterhead line above it
     * is smaller still. Taking the tallest line above the header row therefore finds the section
     * without needing to know the college's wording, and without a body row being able to win by
     * accident.
     */
    private static String detectSectionHeading(List<Row> rows, float headerCenterY) {
        List<Row> candidates = rows.stream()
                .filter(row -> row.centerY() < headerCenterY - ROW_TOLERANCE)
                .filter(row -> Float.isFinite(row.centerY()))
                .filter(row -> !row.words().isEmpty())
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }

        float medianHeight = median(candidates.stream()
                .map(row -> row.words().stream().map(TextWord::height).max(Float::compare).orElse(0f))
                .toList());
        if (medianHeight <= 0f) {
            return null;
        }

        Row heading = null;
        float best = 0f;
        for (Row row : candidates) {
            float height = row.words().stream().map(TextWord::height).max(Float::compare).orElse(0f);
            // A heading must stand clearly out from ordinary text to be believed.
            if (height >= medianHeight * 1.4f && height > best) {
                best = height;
                heading = row;
            }
        }
        if (heading == null) {
            return null;
        }
        String text = String.join(" ",
                heading.words().stream().map(TextWord::text).toList()).strip().replaceAll("\\s+", " ");
        return text.isBlank() ? null : text;
    }

    private static float median(List<Float> values) {
        if (values.isEmpty()) {
            return 0f;
        }
        List<Float> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    /**
     * Flags weekdays that received an unusual number of periods.
     *
     * <p>A college timetable is normally rectangular: every day band holds a similar number of
     * rows. A day that came out noticeably fuller or emptier usually means a row of text was
     * attributed to the wrong band, which is exactly the kind of thing a human must check before
     * the rows are imported. Saying so beats presenting a confident but shifted answer.
     */
    private static List<String> unevenDayWarning(int pageNumber, List<Cell> cells) {
        Map<Integer, Integer> perDay = new java.util.TreeMap<>();
        for (Cell cell : cells) {
            perDay.merge(cell.dayOfWeek(), 1, Integer::sum);
        }
        if (perDay.size() < 2) {
            return List.of();
        }
        List<Integer> counts = new ArrayList<>(perDay.values());
        Collections.sort(counts);
        int median = counts.get(counts.size() / 2);
        if (median == 0) {
            return List.of();
        }
        // A day that is noticeably short is just as suspicious as one that is unusually full: it
        // usually means rows above or below the boundary were attributed to a neighbouring day.
        int tolerance = Math.max(1, Math.round(median * 0.25f));
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : perDay.entrySet()) {
            if (Math.abs(entry.getValue() - median) > tolerance) {
                warnings.add("Page " + pageNumber + ": " + dayName(entry.getKey()) + " has "
                        + entry.getValue() + " periods against a median of " + median
                        + ". Some rows may belong to a neighbouring day; check them before approving.");
            }
        }
        return warnings;
    }

    private static DayHit nearestDay(List<DayHit> dayHits, float rowY) {
        DayHit best = dayHits.get(0);
        float bestDistance = Math.abs(rowY - best.rowY());
        for (DayHit candidate : dayHits) {
            float distance = Math.abs(rowY - candidate.rowY());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static DayHit dayLabel(Row row) {
        List<TextWord> ordered = row.words().stream()
                .sorted((a, b) -> Float.compare(a.x(), b.x()))
                .toList();
        StringBuilder candidate = new StringBuilder();
        float rightEdge = 0f;
        for (TextWord word : ordered) {
            if (candidate.length() > 8) {
                return null;
            }
            String letters = word.text().replaceAll("[^A-Za-z]", "");
            if (letters.isEmpty()) {
                continue;
            }
            candidate.append(letters);
            rightEdge = word.right();
            if (candidate.length() >= 2) {
                Integer day = DAYS.get(candidate.toString().toLowerCase(Locale.ROOT));
                if (day != null) {
                    return new DayHit(day, candidate.toString(), rightEdge, row.centerY());
                }
            }
        }
        return null;
    }

    private static Map<String, Integer> buildDayMap() {
        Map<String, Integer> days = new LinkedHashMap<>();
        days.put("mon", 1);
        days.put("monday", 1);
        days.put("tue", 2);
        days.put("tu", 2);
        days.put("tues", 2);
        days.put("tuesday", 2);
        days.put("wed", 3);
        days.put("we", 3);
        days.put("weds", 3);
        days.put("wednesday", 3);
        days.put("thu", 4);
        days.put("th", 4);
        days.put("thur", 4);
        days.put("thurs", 4);
        days.put("thursday", 4);
        days.put("fri", 5);
        days.put("fr", 5);
        days.put("friday", 5);
        days.put("sat", 6);
        days.put("sa", 6);
        days.put("saturday", 6);
        days.put("sun", 7);
        days.put("su", 7);
        days.put("sunday", 7);
        // German abbreviations, common in exported academic timetables.
        days.put("mo", 1);
        days.put("di", 2);
        days.put("mi", 3);
        days.put("do", 4);
        days.put("so", 7);
        return days;
    }

    /** Finds the time-slot columns of a grid. Replaceable for other templates. */
    public interface ColumnBoundaryDetector {
        java.util.Optional<Header> detect(List<Row> rows);
    }

    /**
     * Default strategy: a header row that lists at least two "HH:MM - HH:MM" ranges. The
     * horizontal midpoint between consecutive ranges becomes the column boundary.
     */
    public static final class HeaderTimeSlotDetector implements ColumnBoundaryDetector {

        @Override
        public Optional<Header> detect(List<Row> rows) {
            for (Row row : rows) {
                List<TextWord> ordered = row.words().stream()
                        .sorted((a, b) -> Float.compare(a.centerX(), b.centerX()))
                        .toList();

                // Work on the joined line, then map character offsets back to word positions.
                String line = String.join(" ", ordered.stream().map(TextWord::text).toList());
                Matcher matcher = TIME_RANGE.matcher(line);

                List<float[]> spans = new ArrayList<>();
                List<String> starts = new ArrayList<>();
                List<String> ends = new ArrayList<>();
                while (matcher.find()) {
                    String start = TimeText.format(TimeText.parse(matcher.group("a")));
                    String end = TimeText.format(TimeText.parse(matcher.group("b")));
                    if (start == null || end == null) {
                        continue;
                    }
                    spans.add(new float[]{xOf(ordered, matcher.start()), xOf(ordered, matcher.end() - 1)});
                    starts.add(start);
                    ends.add(end);
                }

                if (spans.size() < 2) {
                    continue;
                }

                float firstLeft = spans.get(0)[0];
                float headerCenterY = row.centerY();
                List<TimeSlot> slots = new ArrayList<>();
                for (int i = 0; i < spans.size(); i++) {
                    float left = i == 0 ? firstLeft : (spans.get(i - 1)[1] + spans.get(i)[0]) / 2f;
                    float right = i == spans.size() - 1
                            ? spans.get(i)[1] + COLUMN_TOLERANCE * 4
                            : (spans.get(i)[1] + spans.get(i + 1)[0]) / 2f;
                    slots.add(new TimeSlot("slot" + (i + 1), starts.get(i), ends.get(i), left, right));
                }
                return Optional.of(new Header(List.copyOf(slots), headerCenterY));
            }
            return Optional.empty();
        }

        private static float xOf(List<TextWord> ordered, int characterIndex) {
            int seen = 0;
            for (TextWord word : ordered) {
                int length = word.text().length();
                if (characterIndex < seen + length) {
                    return word.centerX();
                }
                seen += length + 1;
            }
            return ordered.isEmpty() ? 0f : ordered.get(ordered.size() - 1).centerX();
        }
    }
}