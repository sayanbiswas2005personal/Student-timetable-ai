package com.college.timetable.service.pdf;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.college.timetable.config.ImportProperties;
import com.college.timetable.exception.PdfImportException;

/**
 * Extracts text from a PDF using Apache PDFBox 3.
 *
 * <p>The important behaviour here is not the happy path: it is deciding, page by page, whether
 * the page actually contains usable text. A scanned timetable produces a perfectly valid PDF that
 * yields an empty string, and treating that as "an empty timetable" would silently destroy data.
 * Such pages are reported as insufficient and routed to OCR instead.
 */
@Service
public class PdfTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    private final ImportProperties properties;

    public PdfTextExtractor(ImportProperties properties) {
        this.properties = properties;
    }

    public record ExtractionResult(List<PageText> pages, int pageCount) {

        public List<Integer> pagesNeedingOcr() {
            return pages.stream().filter(page -> !page.sufficient()).map(PageText::pageNumber).toList();
        }

        public boolean needsOcr() {
            return !pagesNeedingOcr().isEmpty();
        }
    }

    /**
     * Validates the file header, opens the document and extracts every page.
     *
     * @throws PdfImportException when the file is not a readable PDF or exceeds the page limit
     */
    public ExtractionResult extract(Path pdfPath) {
        validateMagicBytes(pdfPath);

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new PdfImportException("PDF_EMPTY", "The PDF contains no pages.");
            }
            if (pageCount > properties.getMaxPages()) {
                throw new PdfImportException("PDF_TOO_MANY_PAGES",
                        "This PDF has " + pageCount + " pages; the limit is " + properties.getMaxPages() + ".");
            }

            List<PageText> pages = new ArrayList<>(pageCount);
            for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
                pages.add(extractPage(document, pageNumber));
            }
            log.info("Extracted {} pages from {} ({} needing OCR)", pageCount, pdfPath.getFileName(),
                    pages.stream().filter(page -> !page.sufficient()).count());
            return new ExtractionResult(List.copyOf(pages), pageCount);
        } catch (PdfImportException ex) {
            throw ex;
        } catch (IOException ex) {
            log.warn("PDF could not be read: {}", pdfPath);
            throw new PdfImportException("PDF_UNREADABLE",
                    "The PDF could not be read. It may be corrupt or password protected.");
        }
    }

    private PageText extractPage(PDDocument document, int pageNumber) throws IOException {
        WordStripper stripper = new WordStripper();
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        stripper.setSortByPosition(true);
        stripper.setSpacingTolerance(0.6f);
        stripper.setSuppressDuplicateOverlappingText(true);

        String text = stripper.getText(document);
        List<TextWord> words = stripper.getWords();

        int chars = text == null ? 0 : text.strip().length();
        boolean sufficient = chars >= properties.getMinCharsPerPage() && !words.isEmpty();

        String reason = null;
        if (!sufficient) {
            reason = chars == 0
                    ? "No embedded text. This page is probably a scan and needs OCR."
                    : "Only " + chars + " characters of text were found, below the configured minimum of "
                            + properties.getMinCharsPerPage() + ". Needs OCR.";
        }
        return new PageText(pageNumber, text == null ? "" : text, words, sufficient, reason);
    }

    /** Rejects anything whose first bytes are not the PDF signature. */
    private void validateMagicBytes(Path path) {
        byte[] header = new byte[4];
        try (var stream = java.nio.file.Files.newInputStream(path)) {
            int read = stream.read(header);
            if (read < 4) {
                throw new PdfImportException("NOT_A_PDF", "The uploaded file is too short to be a PDF.");
            }
        } catch (IOException ex) {
            throw new PdfImportException("PDF_UNREADABLE", "The uploaded file could not be read.");
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (header[i] != PDF_MAGIC[i]) {
                throw new PdfImportException("NOT_A_PDF",
                        "The uploaded file is not a PDF. Only PDF timetable files are accepted.");
            }
        }
    }

    /**
     * Collects both the plain text of a page and every word with its position.
     *
     * <p>PDFBox reports one {@link TextPosition} per glyph, so the raw output arrives as
     * {@code "M"}, {@code "i"}, {@code "n"} ... Left like that the timetable cell text would read
     * "M i n o r _ C S E 1 4 0 5 0" and no subject pattern would ever match. Glyphs that sit on the
     * same line and close enough to be part of the same word are therefore merged back together.
     */
    static final class WordStripper extends PDFTextStripper {

        /** Horizontal gap, as a fraction of glyph height, above which a new word starts. */
        private static final float WORD_GAP_RATIO = 0.32f;
        private static final float SAME_LINE_TOLERANCE = 0.5f;

        private final List<TextWord> words = new ArrayList<>();

        private final StringBuilder buffer = new StringBuilder();
        private float bufferX = Float.NaN;
        private float bufferY = Float.NaN;
        private float bufferWidth = 0f;
        private float bufferHeight = 0f;

        WordStripper() throws IOException {
            super();
        }

        List<TextWord> getWords() {
            flush();
            return words;
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            super.writeString(text, positions);
            for (TextPosition position : positions) {
                String value = position.getUnicode();
                if (value == null || value.isEmpty()) {
                    continue;
                }
                float x = position.getXDirAdj();
                float y = position.getYDirAdj();
                float width = position.getWidthDirAdj();
                float height = position.getHeightDir();

                // Some fonts, including the vector fonts colleges use for logos, report positions
                // that are not finite numbers. Letting one through would poison the row grouping
                // with a NaN that silently swallows every other word on the page, so they are
                // dropped here and the plain text extraction is unaffected.
                if (!Float.isFinite(x) || !Float.isFinite(y)
                        || !Float.isFinite(width) || !Float.isFinite(height)) {
                    continue;
                }
                height = Math.max(height, 1f);

                boolean sameLine = Math.abs(y - bufferY) <= SAME_LINE_TOLERANCE;
                boolean adjacent = x >= bufferX && (x - (bufferX + bufferWidth)) <= height * WORD_GAP_RATIO;

                if (buffer.length() > 0 && sameLine && adjacent && !value.equals(" ")) {
                    buffer.append(value);
                    bufferWidth = x + width - bufferX;
                    bufferHeight = Math.max(bufferHeight, height);
                } else {
                    flush();
                    if (value.equals(" ")) {
                        // A real space ends the current word but starts no new one.
                        continue;
                    }
                    buffer.append(value);
                    bufferX = x;
                    bufferY = y;
                    bufferWidth = width;
                    bufferHeight = height;
                }
            }
        }

        private void flush() {
            if (buffer.length() == 0) {
                return;
            }
            words.add(new TextWord(buffer.toString(), bufferX, bufferY, bufferWidth, bufferHeight));
            buffer.setLength(0);
            bufferX = Float.NaN;
            bufferY = Float.NaN;
            bufferWidth = 0f;
            bufferHeight = 0f;
        }
    }
}