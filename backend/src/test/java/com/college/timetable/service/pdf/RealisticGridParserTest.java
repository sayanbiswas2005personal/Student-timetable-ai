package com.college.timetable.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.college.timetable.config.ImportProperties;

/**
 * The grid parser against a page shaped like a real college timetable: a letterhead, a header of
 * eight time slots, five weekday bands, and multi line cells.
 *
 * <p>Two bugs that only appear on documents like this were found this way, and both are guarded
 * here: the first word of a row never initialised that row's vertical position, which left a NaN
 * that passed every bounds check and pulled page furniture into a cell; and a weekday label that
 * sits in the middle of its own band, which caused the upper half of a day to be attributed to the
 * previous day.
 */
class RealisticGridParserTest {

    private final ImportProperties properties = new ImportProperties();
    private final TimetableGridParser parser = TimetableGridParser.standard();

    private static final float HEADER_Y = 340f;
    private static final float ROW = 14f;
    private static final float SLOT_WIDTH = 60f;
    private static final float FIRST_SLOT_X = 70f;
    private static final float DAY_X = 20f;

    /** Five weekday bands, each with several slots, some of them multi line. */
    private Path writeTimetablePage(Path path) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(560, 400));
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                text(stream, font, 30, 372, "w.e.f. 23/07/2026");
                text(stream, font, 30, 362, "B.TECH CSE III A");
                text(stream, font, 30, 354, "Adamas University, Kolkata 700 126");

                for (int slot = 1; slot <= 8; slot++) {
                    text(stream, font, FIRST_SLOT_X + (slot - 1) * SLOT_WIDTH, HEADER_Y + 14,
                            slot + ".");
                    text(stream, font, FIRST_SLOT_X + (slot - 1) * SLOT_WIDTH, HEADER_Y,
                            timeRange(slot));
                }

                String[] days = {"Mo", "Tu", "We", "Th", "Fr"};
                for (int d = 0; d < days.length; d++) {
                    // The label is drawn near the top of its band, slightly below the first line.
                    float bandTop = HEADER_Y - 30f - d * 50f;
                    text(stream, font, DAY_X, bandTop - 6f, days[d]);

                    // Two lines per slot, and only some slots filled: a realistic ragged grid.
                    for (int slot = 0; slot < 5; slot++) {
                        if ((d + slot) % 4 == 3) {
                            continue;
                        }
                        float x = FIRST_SLOT_X + slot * SLOT_WIDTH + 6f;
                        text(stream, font, x, bandTop, "Subject " + days[d] + (slot + 1));
                        text(stream, font, x, bandTop - ROW, "(CSE1" + (1000 + d * 10 + slot) + ")");
                    }
                }
            }
            document.save(path.toFile());
        }
        return path;
    }

    /** Slot 1 starts at 09:30 and each later slot starts an hour after the previous one. */
    private static String timeRange(int slot) {
        int start = 9 * 60 + 30 + (slot - 1) * 60;
        int end = start + 55;
        return String.format("%02d:%02d - %02d:%02d",
                start / 60, start % 60, end / 60, end % 60);
    }

    private static void text(PDPageContentStream stream, PDType1Font font, float x, float y, String value)
            throws IOException {
        stream.beginText();
        stream.setFont(font, 7);
        stream.newLineAtOffset(x, y);
        stream.showText(value);
        stream.endText();
    }

    private TimetableGridParser.PageParseResult parseFirstPage(Path pdf) throws IOException {
        var page = new PdfTextExtractor(properties).extract(pdf).pages().get(0);
        return parser.parse(page);
    }

    @Test
    @DisplayName("page furniture above the grid never ends up inside a cell")
    void pageFurnitureIsExcluded(@TempDir Path dir) throws IOException {
        var result = parseFirstPage(writeTimetablePage(dir.resolve("timetable.pdf")));

        assertThat(result.cells()).isNotEmpty();
        assertThat(result.cells()).allSatisfy(cell -> {
            assertThat(cell.rawText()).doesNotContain("w.e.f.");
            assertThat(cell.rawText()).doesNotContain("Adamas");
            assertThat(cell.rawText()).doesNotContain("B.TECH");
        });
    }

    @Test
    @DisplayName("every weekday band is detected and given its own cells")
    void everyWeekdayIsFound(@TempDir Path dir) throws IOException {
        var result = parseFirstPage(writeTimetablePage(dir.resolve("timetable.pdf")));

        for (int day = 1; day <= 5; day++) {
            final int expectedDay = day;
            assertThat(result.cells())
                    .as("cells for weekday %s", day)
                    .anyMatch(cell -> cell.dayOfWeek() == expectedDay);
        }
    }

    @Test
    @DisplayName("rows near a weekday label belong to that weekday, not the previous one")
    void rowsAreNotStolenByThePreviousWeekday(@TempDir Path dir) throws IOException {
        var result = parseFirstPage(writeTimetablePage(dir.resolve("timetable.pdf")));

        var monday = result.cells().stream().filter(cell -> cell.dayOfWeek() == 1).toList();
        var tuesday = result.cells().stream().filter(cell -> cell.dayOfWeek() == 2).toList();

        // Each weekday's cells must carry that weekday's own label, never a neighbour's.
        assertThat(monday).allSatisfy(cell -> assertThat(cell.rawText()).contains("Mo"));
        assertThat(tuesday).allSatisfy(cell -> assertThat(cell.rawText()).contains("Tu"));
        assertThat(monday).isNotEmpty();
        assertThat(tuesday).isNotEmpty();
    }

    @Test
    @DisplayName("a multi line cell is read as one period, not two")
    void multiLineCellsAreOnePeriod(@TempDir Path dir) throws IOException {
        var result = parseFirstPage(writeTimetablePage(dir.resolve("timetable.pdf")));

        var monday = result.cells().stream().filter(cell -> cell.dayOfWeek() == 1).toList();

        // Two visual lines, one period: exactly one subject code per cell, never two rows.
        assertThat(monday).isNotEmpty();
        assertThat(monday).allSatisfy(cell ->
                assertThat(cell.rawText().split("\\(CSE1", -1).length - 1)
                        .as("subject codes in %s", cell.rawText())
                        .isEqualTo(1));
        assertThat(monday).allSatisfy(cell ->
                assertThat(cell.rawText()).containsPattern("Subject Mo\\d \\(CSE1\\d+\\)"));
    }

    @Test
    @DisplayName("each cell keeps the time slot it was read from")
    void cellsKeepTheirTimeSlot(@TempDir Path dir) throws IOException {
        var result = parseFirstPage(writeTimetablePage(dir.resolve("timetable.pdf")));

        assertThat(result.cells()).allSatisfy(cell -> {
            assertThat(cell.startTime()).matches("\\d{2}:\\d{2}");
            assertThat(cell.endTime()).matches("\\d{2}:\\d{2}");
            assertThat(java.time.LocalTime.parse(cell.endTime()))
                    .isAfter(java.time.LocalTime.parse(cell.startTime()));
        });
        // Times come from the header, so within one weekday they run in slot order.
        for (int day = 1; day <= 5; day++) {
            final int expectedDay = day;
            assertThat(result.cells().stream()
                    .filter(cell -> cell.dayOfWeek() == expectedDay)
                    .map(TimetableGridParser.Cell::startTime))
                    .as("slot order for weekday %s", day)
                    .isSorted();
        }
    }

    @Test
    @DisplayName("an empty slot produces no cell, so it stays a free period")
    void emptySlotsProduceNothing(@TempDir Path dir) throws IOException {
        var result = parseFirstPage(writeTimetablePage(dir.resolve("timetable.pdf")));

        // The fixture deliberately leaves slots empty; the parser must not invent periods for them.
        assertThat(result.cells().stream().map(TimetableGridParser.Cell::startTime).distinct().count())
                .isLessThan(8);
    }

    @Test
    @DisplayName("the content parser reads the subject out of a read cell")
    void contentParserReadsTheCell(@TempDir Path dir) throws IOException {
        var result = parseFirstPage(writeTimetablePage(dir.resolve("timetable.pdf")));
        var cellParser = new CellContentParser();

        var parsed = result.cells().stream().map(cell -> cellParser.parse(cell.rawText())).toList();

        assertThat(parsed).anySatisfy(cell -> {
            assertThat(cell.subjectCode()).matches("CSE1\\d{4}");
            assertThat(cell.subjectName()).contains("Subject");
        });
    }

    @Test
    @DisplayName("a scanned page with no text produces no cells and says why")
    void scannedPageIsReported(@TempDir Path dir) throws IOException {
        Path pdf = dir.resolve("scan.pdf");
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 600, 400);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 14));
        graphics.drawString("Mo  9:30 - 10:25  Cloud Computing", 20, 60);
        graphics.dispose();
        byte[] png;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            png = out.toByteArray();
        }
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.drawImage(org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
                        .createFromByteArray(document, png, "png"), 0, 0, 600, 400);
            }
            document.save(pdf.toFile());
        }

        var extraction = new PdfTextExtractor(properties).extract(pdf);
        var result = parser.parse(extraction.pages().get(0));

        assertThat(extraction.needsOcr()).isTrue();
        assertThat(result.cells()).isEmpty();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("OCR"));
    }
}