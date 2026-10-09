package com.college.timetable.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

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
 * End to end test of the reading pipeline against a PDF this test builds itself.
 *
 * <p>It goes from a real file on disk through PDFBox, the grid parser and the cell parser, which
 * is the only way to know that column boundaries survive the round trip.
 */
class TimetableGridParserTest {

    private final ImportProperties properties = new ImportProperties();
    private final TimetableGridParser parser = TimetableGridParser.standard();

    private static final float MARGIN = 40f;
    private static final float ROW = 20f;
    private static final float START_Y = 780f;

    /** Writes a small timetable grid: a header of time slots, then one row per weekday. */
    private Path writeGridPdf(Path path) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                drawGridLines(stream);

                // Header: slot numbers and time ranges.
                text(stream, font, 40, START_Y, "1.");
                text(stream, font, 80, START_Y, "9:30 - 10:25");
                text(stream, font, 240, START_Y, "2.");
                text(stream, font, 280, START_Y, "10:30 - 11:25");
                text(stream, font, 440, START_Y, "3.");
                text(stream, font, 480, START_Y, "11:30 - 12:25");

                // Monday.
                text(stream, font, MARGIN, START_Y - ROW * 2, "Mo");
                text(stream, font, 60, START_Y - ROW * 2, "Cloud Computing");
                text(stream, font, 60, START_Y - ROW * 2 - ROW / 2, "(CSE11036)");
                text(stream, font, 220, START_Y - ROW * 2, "Natural Language");
                text(stream, font, 220, START_Y - ROW * 2 - ROW / 2, "Processing (CSE11209)");
                text(stream, font, 420, START_Y - ROW * 2, "Library");

                // Tuesday, first column intentionally left free.
                text(stream, font, MARGIN, START_Y - ROW * 4, "Tu");
                text(stream, font, 240, START_Y - ROW * 4, "MINOR_CSE14050");
                text(stream, font, 440, START_Y - ROW * 4, "AU6-4304");
            }
            document.save(path.toFile());
        }
        return path;
    }

    private static void drawGridLines(PDPageContentStream stream) throws IOException {
        stream.setStrokingColor(Color.LIGHT_GRAY);
        stream.setLineWidth(0.5f);
        float[] verticals = {MARGIN, 200, 380, 560, 555};
        for (float x : verticals) {
            stream.moveTo(x, START_Y + 12);
            stream.lineTo(x, START_Y - ROW * 6);
            stream.stroke();
        }
        for (int i = 0; i <= 6; i++) {
            float y = START_Y + 12 - i * ROW;
            stream.moveTo(MARGIN, y);
            stream.lineTo(560, y);
            stream.stroke();
        }
    }

    private static void text(PDPageContentStream stream, PDType1Font font, float x, float y, String value)
            throws IOException {
        stream.beginText();
        stream.setFont(font, 9);
        stream.newLineAtOffset(x, y);
        stream.showText(value);
        stream.endText();
    }

    @Test
    @DisplayName("a timetable grid becomes one cell per weekday and time slot")
    void readsGridIntoCells(@TempDir Path dir) throws IOException {
        Path pdf = writeGridPdf(dir.resolve("grid.pdf"));
        var page = new PdfTextExtractor(properties).extract(pdf).pages().get(0);

        var result = parser.parse(page);

        assertThat(result.cells()).isNotEmpty();
        assertThat(result.cells()).anySatisfy(cell -> {
            assertThat(cell.dayOfWeek()).isEqualTo(1);
            assertThat(cell.startTime()).isEqualTo("09:30");
            assertThat(cell.endTime()).isEqualTo("10:25");
            assertThat(cell.rawText()).contains("Cloud Computing");
        });
        assertThat(result.cells()).anySatisfy(cell -> {
            assertThat(cell.dayOfWeek()).isEqualTo(1);
            assertThat(cell.startTime()).isEqualTo("11:30");
            assertThat(cell.rawText()).contains("Library");
        });
        assertThat(result.cells()).anySatisfy(cell -> {
            assertThat(cell.dayOfWeek()).isEqualTo(2);
            assertThat(cell.rawText()).contains("MINOR_CSE14050");
        });
    }

    @Test
    @DisplayName("an empty slot produces no cell, so it stays a free period rather than a fake subject")
    void emptySlotProducesNoCell(@TempDir Path dir) throws IOException {
        Path pdf = writeGridPdf(dir.resolve("grid.pdf"));
        var page = new PdfTextExtractor(properties).extract(pdf).pages().get(0);

        var mondayFirstSlot = parser.parse(page).cells().stream()
                .filter(cell -> cell.dayOfWeek() == 2 && cell.startTime().equals("09:30"))
                .toList();

        assertThat(mondayFirstSlot).isEmpty();
    }

    @Test
    @DisplayName("cell text feeds the content parser cleanly")
    void cellsFeedTheContentParser(@TempDir Path dir) throws IOException {
        Path pdf = writeGridPdf(dir.resolve("grid.pdf"));
        var page = new PdfTextExtractor(properties).extract(pdf).pages().get(0);
        var cellParser = new CellContentParser();

        var parsed = parser.parse(page).cells().stream()
                .map(cell -> cellParser.parse(cell.rawText()))
                .toList();

        assertThat(parsed).anySatisfy(cell -> {
            assertThat(cell.subjectCode()).isEqualTo("CSE11036");
            assertThat(cell.subjectName()).isNotBlank();
        });
        assertThat(parsed).anySatisfy(cell -> assertThat(cell.subjectCode()).isEqualTo("CSE11209"));
    }

    @Test
    @DisplayName("a page with no time slot header is reported rather than silently parsed as empty")
    void pageWithoutHeaderIsReported(@TempDir Path dir) throws IOException {
        Path pdf = dir.resolve("prose.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                String line = "This document is a notice rather than a timetable grid with slots.";
                for (int i = 0; i < 3; i++) {
                    text(stream, font, 40, 700 - i * 20, line);
                }
            }
            document.save(pdf.toFile());
        }

        var page = new PdfTextExtractor(properties).extract(pdf).pages().get(0);
        var result = parser.parse(page);

        assertThat(result.cells()).isEmpty();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("no recognisable time-slot header"));
    }

    @Test
    @DisplayName("a page with too little text is skipped with the OCR reason attached")
    void insufficientPageIsSkipped() {
        PageText page = new PageText(3, "", List.of(), false, "No embedded text. Needs OCR.");

        var result = parser.parse(page);

        assertThat(result.cells()).isEmpty();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("OCR"));
    }

    @Test
    @DisplayName("a scanned page produces no text at all, which is why OCR exists")
    void scannedPageHasNoText(@TempDir Path dir) throws IOException {
        Path pdf = dir.resolve("scan.pdf");
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 600, 400);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 16));
        graphics.drawString("Mo 9:30 - 10:25 Cloud Computing", 20, 60);
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

        assertThat(extraction.needsOcr()).isTrue();
        assertThat(extraction.pages().get(0).characterCount()).isZero();
    }
}