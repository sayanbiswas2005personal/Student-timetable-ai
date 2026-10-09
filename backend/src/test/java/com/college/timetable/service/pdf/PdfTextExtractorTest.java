package com.college.timetable.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.college.timetable.config.ImportProperties;
import com.college.timetable.exception.PdfImportException;

class PdfTextExtractorTest {

    private final ImportProperties properties = new ImportProperties();

    private PdfTextExtractor extractor() {
        return new PdfTextExtractor(properties);
    }

    private static Path writeTextPdf(Path path, List<List<String>> linesPerPage) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (List<String> lines : linesPerPage) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    stream.setLeading(14f);
                    stream.newLineAtOffset(40, 750);
                    for (String line : lines) {
                        stream.showText(sanitize(line));
                        stream.newLine();
                    }
                    stream.endText();
                }
            }
            document.save(path.toFile());
        }
        return path;
    }

    /** PDFBox standard fonts cannot encode characters outside WinAnsi. */
    private static String sanitize(String text) {
        return text.chars()
                .map(c -> c < 256 ? c : '-')
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    @Test
    @DisplayName("a digital PDF yields its text page by page with the page numbers preserved")
    void extractsTextFromDigitalPdf(@TempDir Path dir) throws IOException {
        Path pdf = writeTextPdf(dir.resolve("timetable.pdf"), List.of(
                List.of("Mo", "1.", "9:30 - 10:25", "MINOR_CSE14050", "Dr. A Sen (56312)"),
                List.of("Tu", "1.", "9:30 - 10:25", "Cloud Computing (CSE11036)", "AU6-4304")));

        var result = extractor().extract(pdf);

        assertThat(result.pageCount()).isEqualTo(2);
        assertThat(result.pages()).hasSize(2);
        assertThat(result.pages().get(0).pageNumber()).isEqualTo(1);
        assertThat(result.pages().get(0).text()).contains("9:30 - 10:25");
        assertThat(result.pages().get(1).pageNumber()).isEqualTo(2);
        assertThat(result.pages().get(1).text()).contains("CSE11036");
        assertThat(result.needsOcr()).isFalse();
    }

    @Test
    @DisplayName("a scanned style PDF that only contains an image is reported as needing OCR")
    void imageOnlyPageIsFlaggedForOcr(@TempDir Path dir) throws IOException {
        Path pdf = dir.resolve("scan.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            var image = new BufferedImageBuilder().build();
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                PDImageXObject xObject = PDImageXObject.createFromByteArray(document, image, "png");
                stream.drawImage(xObject, 0, 0, 595, 842);
            }
            document.save(pdf.toFile());
        }

        var result = extractor().extract(pdf);

        assertThat(result.pageCount()).isEqualTo(1);
        assertThat(result.pages().get(0).text()).isBlank();
        assertThat(result.pages().get(0).sufficient()).isFalse();
        assertThat(result.pages().get(0).reason()).contains("OCR");
        assertThat(result.needsOcr()).isTrue();
        assertThat(result.pagesNeedingOcr()).containsExactly(1);
    }

    @Test
    @DisplayName("only the pages with too little text are flagged, not the whole document")
    void onlySparsePagesAreFlagged(@TempDir Path dir) throws IOException {
        Path pdf = writeTextPdf(dir.resolve("mixed.pdf"), List.of(
                List.of("Mo", "9:30 - 10:25", "MINOR_CSE14050", "Dr. Amitava Sen", "AU6-4304", "Library"),
                List.of("Mo")));

        var result = extractor().extract(pdf);

        assertThat(result.pages().get(0).sufficient()).isTrue();
        assertThat(result.pages().get(1).sufficient()).isFalse();
        assertThat(result.pagesNeedingOcr()).containsExactly(2);
    }

    @Test
    @DisplayName("a file that is not a PDF is rejected on its content, not its name")
    void rejectsNonPdf(@TempDir Path dir) throws IOException {
        Path notAPdf = dir.resolve("timetable.pdf");
        Files.writeString(notAPdf, "this is plain text, not a PDF at all");

        assertThatThrownBy(() -> extractor().extract(notAPdf))
                .isInstanceOf(PdfImportException.class)
                .satisfies(ex -> assertThat(((PdfImportException) ex).getCode()).isEqualTo("NOT_A_PDF"));
    }

    @Test
    @DisplayName("a truncated or corrupt PDF fails safely instead of crashing the import")
    void rejectsCorruptPdf(@TempDir Path dir) throws IOException {
        Path corrupt = dir.resolve("corrupt.pdf");
        Files.writeString(corrupt, "%PDF-1.7 this looks like a PDF but is not one");

        assertThatThrownBy(() -> extractor().extract(corrupt))
                .isInstanceOf(PdfImportException.class)
                .satisfies(ex -> assertThat(((PdfImportException) ex).getCode()).isEqualTo("PDF_UNREADABLE"));
    }

    @Test
    @DisplayName("an empty file is rejected")
    void rejectsEmptyFile(@TempDir Path dir) throws IOException {
        Path empty = dir.resolve("empty.pdf");
        Files.writeString(empty, "");

        assertThatThrownBy(() -> extractor().extract(empty))
                .isInstanceOf(PdfImportException.class);
    }

    @Test
    @DisplayName("word positions are captured so the grid parser can reconstruct the table")
    void capturesWordPositions(@TempDir Path dir) throws IOException {
        Path pdf = writeTextPdf(dir.resolve("grid.pdf"),
                List.of(List.of("Mo", "1.", "9:30 - 10:25", "2.", "10:30 - 11:25", "Cloud Computing")));

        var page = extractor().extract(pdf).pages().get(0);

        assertThat(page.words()).isNotEmpty();
        assertThat(page.words()).allSatisfy(word -> assertThat(word.text()).isNotBlank());
        assertThat(page.words()).anyMatch(word -> word.text().startsWith("9:30"));
        assertThat(page.words()).anyMatch(word -> word.x() > 0f);
    }

    /** Small helper so the image only PDF test does not depend on an external resource. */
    private static final class BufferedImageBuilder {
        byte[] build() {
            java.awt.image.BufferedImage image =
                    new java.awt.image.BufferedImage(200, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 200, 200);
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font("SansSerif", Font.PLAIN, 14));
            graphics.drawString("9:30 - 10:25", 10, 100);
            graphics.dispose();
            try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                javax.imageio.ImageIO.write(image, "png", out);
                return out.toByteArray();
            } catch (IOException ex) {
                throw new IllegalStateException("Could not build the test image", ex);
            }
        }
    }
}