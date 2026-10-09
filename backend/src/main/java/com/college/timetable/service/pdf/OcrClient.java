package com.college.timetable.service.pdf;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.college.timetable.config.ImportProperties;
import com.college.timetable.exception.PdfImportException;

/**
 * Optional fallback for scanned timetables.
 *
 * <p>PDFBox can only read text that is embedded in the PDF. A scanned timetable has none, so an
 * OCR service is required. The service is a separate Python process (see {@code ocr-service}) so
 * that heavy native dependencies never affect the JVM application.
 *
 * <p>When OCR is disabled or unreachable the import is <em>not</em> silently accepted. The job is
 * left in {@code OCR_PENDING} with the pages that need attention, and a human has to run OCR
 * externally and re-upload. Guessing at scanned text would be worse than asking.
 */
@Service
public class OcrClient {

    private static final Logger log = LoggerFactory.getLogger(OcrClient.class);

    private final ImportProperties properties;
    private final RestClient restClient;

    public OcrClient(ImportProperties properties) {
        this.properties = properties;
        Duration timeout = properties.ocrTimeout();
        this.restClient = RestClient.builder()
                .baseUrl(properties.getOcr().getBaseUrl())
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout((int) timeout.toMillis());
                    setReadTimeout((int) timeout.toMillis());
                }})
                .build();
    }

    /** OCR output for one page. {@code confidence} is the service's own estimate, never invented. */
    public record OcrPage(int pageNumber, String text, double confidence) {
    }

    public record OcrResult(List<OcrPage> pages, boolean used) {
    }

    public boolean isEnabled() {
        return properties.getOcr().isEnabled();
    }

    /**
     * Sends the whole document plus the list of pages that produced too little text.
     *
     * @throws PdfImportException when OCR is unavailable or fails; the caller must surface this
     */
    public OcrResult recognise(byte[] pdf, List<Integer> pageNumbers) {
        if (!isEnabled()) {
            throw new PdfImportException("OCR_NOT_CONFIGURED",
                    "Pages " + pageNumbers + " contain no embedded text and OCR is not enabled. "
                            + "Run OCR externally and upload a text based PDF, or enable the OCR service.");
        }
        try {
            OcrResponse response = restClient.post()
                    .uri("/ocr")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("X-Pages", pageNumbers.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""))
                    .body(pdf)
                    .retrieve()
                    .body(OcrResponse.class);
            if (response == null || response.pages() == null || response.pages().isEmpty()) {
                throw new PdfImportException("OCR_EMPTY",
                        "The OCR service returned no text for pages " + pageNumbers + ".");
            }
            log.info("OCR returned {} pages", response.pages().size());
            return new OcrResult(response.pages(), true);
        } catch (PdfImportException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("OCR service call failed: {}", ex.getMessage());
            throw new PdfImportException("OCR_FAILED",
                    "The OCR service could not be reached or failed. Pages " + pageNumbers
                            + " still need text extraction.");
        }
    }

    record OcrResponse(List<OcrPage> pages, String engine) {
    }
}