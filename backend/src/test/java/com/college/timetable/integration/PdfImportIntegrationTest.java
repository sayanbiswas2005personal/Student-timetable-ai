package com.college.timetable.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.college.timetable.dto.imports.ApproveImportRequest;
import com.college.timetable.dto.imports.ImportCandidateDto;
import com.college.timetable.dto.imports.UpdateCandidateRequest;
import com.college.timetable.dto.timetable.TimetableDto;
import com.college.timetable.entity.ImportStatus;
import com.college.timetable.entity.ReviewStatus;
import com.college.timetable.repository.AcademicTermRepository;
import com.college.timetable.repository.DepartmentRepository;
import com.college.timetable.repository.FacultyRepository;
import com.college.timetable.repository.ImportJobRepository;
import com.college.timetable.repository.ProgramRepository;
import com.college.timetable.repository.RoomRepository;
import com.college.timetable.repository.SectionRepository;
import com.college.timetable.repository.StudentRepository;
import com.college.timetable.repository.SubjectRepository;
import com.college.timetable.repository.TimetableEntryRepository;
import com.college.timetable.repository.TimetableRepository;
import com.college.timetable.service.importreview.ImportService;
import com.college.timetable.service.lookup.StudentLookupService;
import com.college.timetable.service.pdf.OcrClient;
import com.college.timetable.service.timetable.TimetableService;
import com.college.timetable.support.TestFixtures;
import com.college.timetable.support.TestFixtures.Catalog;

/**
 * The administrator import workflow, end to end:
 * upload a PDF, review the extracted rows, correct one, approve into a draft, publish, then
 * confirm the lookup engine serves the imported data.
 *
 * <p>The point of the test is that parser output never reaches a published timetable on its own:
 * an unapproved row produces nothing, and only the rows a human accepted are imported.
 */
@SpringBootTest(properties = {
        "college.seed-demo-data=false",
        "college.import.storage-dir=./target/test-imports"
})
@Import(TestFixtures.class)
@ActiveProfiles("test")
@Transactional
class PdfImportIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    @Autowired private DepartmentRepository departments;
    @Autowired private ProgramRepository programs;
    @Autowired private AcademicTermRepository terms;
    @Autowired private SectionRepository sections;
    @Autowired private SubjectRepository subjects;
    @Autowired private FacultyRepository faculties;
    @Autowired private RoomRepository rooms;
    @Autowired private StudentRepository students;
    @Autowired private ImportJobRepository jobs;
    @Autowired private TimetableRepository timetables;
    @Autowired private TimetableEntryRepository entries;

    @Autowired private ImportService importService;
    @Autowired private TimetableService timetableService;
    @Autowired private StudentLookupService lookupService;

    /** The scanned-PDF path is covered separately; here the text extraction succeeds. */
    @MockitoBean
    private OcrClient ocrClient;

    private Catalog catalog;

    @BeforeEach
    void setUp() {
        catalog = TestFixtures.catalog(departments, programs, terms, sections, subjects, faculties, rooms);
    }

    private static MockMultipartFile digitalTimetablePdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                draw(stream, font, 40, 780, "1.");
                draw(stream, font, 80, 780, "9:30 - 10:25");
                draw(stream, font, 240, 780, "2.");
                draw(stream, font, 280, 780, "10:30 - 11:25");

                draw(stream, font, 40, 740, "Mo");
                draw(stream, font, 60, 740, "Cloud Computing");
                draw(stream, font, 60, 730, "(CSE11036)");
                draw(stream, font, 240, 740, "Prof. Amitava Sen");
                draw(stream, font, 240, 730, "(56312)");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return new MockMultipartFile("file", "semester-timetable.pdf", "application/pdf", out.toByteArray());
        }
    }

    private static MockMultipartFile scannedTimetablePdf() throws IOException {
        BufferedImage image = new BufferedImage(700, 400, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 700, 400);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 14));
        graphics.drawString("Mo  9:30 - 10:25  Cloud Computing (CSE11036)", 20, 60);
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
                        .createFromByteArray(document, png, "png"), 0, 0, 700, 400);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return new MockMultipartFile("file", "scanned-timetable.pdf", "application/pdf", out.toByteArray());
        }
    }

    private static void draw(PDPageContentStream stream, PDType1Font font, float x, float y, String value)
            throws IOException {
        stream.beginText();
        stream.setFont(font, 10);
        stream.newLineAtOffset(x, y);
        stream.showText(value);
        stream.endText();
    }

    @Test
    @DisplayName("a digital PDF produces reviewable rows, which an administrator then approves")
    void uploadReviewApprovePublish() throws IOException {
        var job = importService.upload(digitalTimetablePdf());

        assertThat(job.status()).isEqualTo(ImportStatus.AWAITING_REVIEW.name());
        assertThat(job.totalPages()).isEqualTo(1);
        assertThat(job.pagesNeedingOcr()).isZero();
        assertThat(job.candidateCount()).isPositive();

        List<ImportCandidateDto> candidates = importService.candidates(job.id());
        var mondaySlot = candidates.stream()
                .filter(c -> c.dayOfWeek() != null && c.dayOfWeek() == 1
                        && "09:30".equals(c.startTime()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The 09:30 Monday row was not extracted"));

        // The raw source text is preserved so a reviewer can check the machine against the page.
        assertThat(mondaySlot.rawText()).contains("Cloud Computing");
        assertThat(mondaySlot.subjectLabel()).contains("Cloud Computing");
        assertThat(mondaySlot.subjectId()).isEqualTo(catalog.subject().getId());
        assertThat(mondaySlot.reviewStatus()).isEqualTo(ReviewStatus.PENDING.name());
        assertThat(mondaySlot.subjectId()).isEqualTo(catalog.subject().getId());

        // Nothing is imported until a human approves it. The reviewer also drops the row that
        // only carries a faculty name, because it maps to no subject.
        for (var candidate : candidates) {
            importService.updateCandidate(job.id(), candidate.id(),
                    candidate.subjectId() == null
                            ? rejected("no subject recognised on this row")
                            : approved(candidate.subjectId(), "checked against page 1"));
        }

        Long timetableId = importService.approve(job.id(),
                new ApproveImportRequest(catalog.section().getId(), TODAY.minusDays(1), null));

        var draft = timetableService.get(timetableId);
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(draft.entries()).isNotEmpty();
        assertThat(draft.entries()).allSatisfy(entry ->
                assertThat(entry.subjectId()).isEqualTo(catalog.subject().getId()));

        // The draft must be published explicitly before the lookup engine will use it.
        TimetableDto published = timetableService.publish(timetableId, null);
        assertThat(published.status()).isEqualTo("PUBLISHED");

        importService.get(job.id());
        assertThat(importService.get(job.id()).status()).isEqualTo(ImportStatus.APPROVED.name());
    }

    @Test
    @DisplayName("a corrected row replaces what the parser guessed")
    void reviewerCanCorrectARow() throws IOException {
        var job = importService.upload(digitalTimetablePdf());
        var candidates = importService.candidates(job.id());
        var first = candidates.get(0);

        var updated = importService.updateCandidate(job.id(), first.id(),
                request(catalog.section().getId(), catalog.subject().getId(), catalog.faculty().getId(),
                catalog.room().getId(), 3, "14:00", "15:00", "CLASS", "D", "corrected subject",
                "corrected faculty", "TEST-ROOM", "moved to Wednesday afternoon", "EDITED"));

        assertThat(updated.reviewStatus()).isEqualTo(ReviewStatus.EDITED.name());
        assertThat(updated.dayOfWeek()).isEqualTo(3);
        assertThat(updated.startTime()).isEqualTo("14:00");
        assertThat(updated.roomLabel()).isEqualTo("TEST-ROOM");
        assertThat(updated.sectionId()).isEqualTo(catalog.section().getId());
        assertThat(updated.reviewerNotes()).isNotBlank();
    }

    @Test
    @DisplayName("a rejected row is not imported")
    void rejectedRowsAreSkipped() throws IOException {
        var job = importService.upload(digitalTimetablePdf());
        var candidates = importService.candidates(job.id());
        var keep = candidates.get(0);
        var drop = candidates.get(1);

        importService.updateCandidate(job.id(), keep.id(),
                approved(catalog.subject().getId(), "verified"));
        importService.updateCandidate(job.id(), drop.id(),
                rejected("this was a header, not a class"));

        importService.candidates(job.id()).forEach(c -> System.out.println("POST id=" + c.id()
                + " subj=" + c.subjectId() + " review=" + c.reviewStatus() + " err=" + c.validationErrors()));
        Long timetableId = importService.approve(job.id(),
                new ApproveImportRequest(catalog.section().getId(), TODAY.minusDays(1), null));

        var draft = timetableService.get(timetableId);
        assertThat(draft.entries()).hasSize(1);
        assertThat(draft.entries().get(0).dayOfWeek()).isEqualTo(keep.dayOfWeek());
        assertThat(draft.entries().get(0).startTime()).isEqualTo(keep.startTime());
    }

    @Test
    @DisplayName("approving with nothing reviewed is refused")
    void cannotApproveWithoutReviewedRows() throws IOException {
        var job = importService.upload(digitalTimetablePdf());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        importService.approve(job.id(),
                                new ApproveImportRequest(catalog.section().getId(), TODAY, null)))
                .isInstanceOf(com.college.timetable.exception.ApiException.class)
                .satisfies(ex -> assertThat(((com.college.timetable.exception.ApiException) ex).getCode())
                        .isEqualTo("NOTHING_TO_APPROVE"));
    }

    @Test
    @DisplayName("an imported and published timetable is what the lookup engine then serves")
    void importedTimetableFeedsTheLookup() throws IOException {
        var job = importService.upload(digitalTimetablePdf());
        var candidates = importService.candidates(job.id());
        var monday = candidates.stream()
                .filter(c -> c.dayOfWeek() != null && c.dayOfWeek() == 1 && "09:30".equals(c.startTime()))
                .findFirst()
                .orElseThrow();

        importService.updateCandidate(job.id(), monday.id(),
                request(null, catalog.subject().getId(), catalog.faculty().getId(), catalog.room().getId(),
                null, null, null, null, null, null, null, null, "verified against page 1",
                "APPROVED"));

        Long timetableId = importService.approve(job.id(),
                new ApproveImportRequest(catalog.section().getId(), TODAY.minusDays(1), null));
        timetableService.publish(timetableId, null);

        var student = TestFixtures.student(students, "UG/02/BTCSEAIML/2023/500", catalog.section());
        // 2026-10-12 is the Monday of the imported grid.
        var mondayMorning = LocalDate.of(2026, 10, 12).atTime(10, 0).atZone(TestFixtures.ZONE);
        var response = lookupService.lookup(student.getRegistrationNumber(), mondayMorning);

        assertThat(response.timetable().sourceFilename()).isEqualTo("semester-timetable.pdf");
        assertThat(response.currentClass().subjectCode()).isEqualTo("CSE11036");
        assertThat(response.currentClass().roomCode()).isEqualTo("AU6-4304");
    }

    @Test
    @DisplayName("a scanned PDF is reported as needing OCR and is never guessed at")
    void scannedPdfNeedsOcr() throws IOException {
        var job = importService.upload(scannedTimetablePdf());

        assertThat(job.pagesNeedingOcr()).isEqualTo(1);
        assertThat(job.status()).isEqualTo(ImportStatus.FAILED.name());
        assertThat(job.errorSummary()).isNotBlank();
        // No rows were invented from a page that had no text.
        assertThat(importService.candidates(job.id())).isEmpty();
    }

    @Test
    @DisplayName("a file that is not a PDF is rejected")
    void rejectsNonPdf() {
        var notAPdf = new MockMultipartFile("file", "timetable.pdf", "application/pdf",
                "plain text pretending to be a PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> importService.upload(notAPdf))
                .isInstanceOf(com.college.timetable.exception.PdfImportException.class)
                .satisfies(ex -> assertThat(((com.college.timetable.exception.PdfImportException) ex)
                        .getCode()).isEqualTo("NOT_A_PDF"));
    }

    @Test
    @DisplayName("a non PDF extension is rejected before anything is stored")
    void rejectsWrongExtension() {
        var wrongExtension = new MockMultipartFile("file", "timetable.xlsx",
                "application/vnd.ms-excel", new byte[]{1, 2, 3});

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> importService.upload(wrongExtension))
                .isInstanceOf(com.college.timetable.exception.PdfImportException.class)
                .satisfies(ex -> assertThat(((com.college.timetable.exception.PdfImportException) ex)
                        .getCode()).isEqualTo("NOT_A_PDF"));
    }

    @Test
    @DisplayName("the internal filename is generated, never taken from the upload")
    void internalFilenameIsGenerated() throws IOException {
        var job = importService.upload(digitalTimetablePdf());
        var stored = jobs.findById(job.id()).orElseThrow().getStoredFilename();

        assertThat(stored).startsWith("import-").endsWith(".pdf");
        assertThat(stored).doesNotContain("semester-timetable");
        assertThat(stored).doesNotContain("/").doesNotContain("..");
    }

    /** Named-argument helper: a 14 field record is unreadable inline. */
    private static UpdateCandidateRequest request(Long sectionId, Long subjectId, Long facultyId, Long roomId,
                                                 Integer dayOfWeek, String startTime, String endTime,
                                                 String entryType, String sectionLabel, String subjectLabel,
                                                 String facultyLabel, String roomLabel, String reviewerNotes,
                                                 String reviewStatus) {
        return new UpdateCandidateRequest(sectionId, subjectId, facultyId, roomId, dayOfWeek, startTime,
                endTime, entryType, sectionLabel, subjectLabel, facultyLabel, roomLabel, reviewerNotes,
                reviewStatus);
    }

    private static UpdateCandidateRequest approved(Long subjectId, String notes) {
        return request(null, subjectId, null, null, null, null, null, null, null, null, null, null,
                notes, "APPROVED");
    }

    private static UpdateCandidateRequest rejected(String notes) {
        return request(null, null, null, null, null, null, null, null, null, null, null, null,
                notes, "REJECTED");
    }
}
