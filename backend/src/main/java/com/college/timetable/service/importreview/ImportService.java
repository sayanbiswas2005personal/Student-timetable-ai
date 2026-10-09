package com.college.timetable.service.importreview;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.college.timetable.config.ImportProperties;
import com.college.timetable.dto.imports.ApproveImportRequest;
import com.college.timetable.dto.imports.ImportCandidateDto;
import com.college.timetable.dto.imports.ImportJobDto;
import com.college.timetable.dto.imports.UpdateCandidateRequest;
import com.college.timetable.entity.EntryType;
import com.college.timetable.entity.Faculty;
import com.college.timetable.entity.ImportCandidate;
import com.college.timetable.entity.ImportJob;
import com.college.timetable.entity.ImportStatus;
import com.college.timetable.entity.Room;
import com.college.timetable.entity.Section;
import com.college.timetable.entity.Subject;
import com.college.timetable.entity.Timetable;
import com.college.timetable.entity.TimetableEntry;
import com.college.timetable.entity.TimetableStatus;
import com.college.timetable.entity.VerificationStatus;
import com.college.timetable.exception.ApiException;
import com.college.timetable.exception.PdfImportException;
import com.college.timetable.repository.FacultyRepository;
import com.college.timetable.repository.ImportCandidateRepository;
import com.college.timetable.repository.ImportJobRepository;
import com.college.timetable.repository.RoomRepository;
import com.college.timetable.repository.SectionRepository;
import com.college.timetable.repository.SubjectRepository;
import com.college.timetable.repository.TimetableEntryRepository;
import com.college.timetable.repository.TimetableRepository;
import com.college.timetable.security.CurrentUser;
import com.college.timetable.service.audit.AuditService;
import com.college.timetable.service.pdf.CellContentParser;
import com.college.timetable.service.pdf.OcrClient;
import com.college.timetable.service.pdf.PageText;
import com.college.timetable.service.pdf.PdfTextExtractor;
import com.college.timetable.service.pdf.TimetableGridParser;
import com.college.timetable.util.TimeText;
import com.college.timetable.util.UploadStorage;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Administrator only PDF import workflow.
 *
 * <p>Pipeline: validate upload, extract text per page, detect pages that need OCR, read the grid,
 * turn cells into reviewable candidates, let a human correct them, and only then build a draft
 * timetable. Nothing is ever published straight from parser output.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    private static final String PDF_EXTENSION = ".pdf";

    private final ImportProperties properties;
    private final PdfTextExtractor extractor;
    private final OcrClient ocrClient;
    private final TimetableGridParser gridParser = TimetableGridParser.standard();
    private final CellContentParser cellParser = new CellContentParser();

    private final ImportJobRepository jobRepository;
    private final ImportCandidateRepository candidateRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final FacultyRepository facultyRepository;
    private final RoomRepository roomRepository;
    private final TimetableRepository timetableRepository;
    private final TimetableEntryRepository entryRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ImportService(ImportProperties properties,
                         PdfTextExtractor extractor,
                         OcrClient ocrClient,
                         ImportJobRepository jobRepository,
                         ImportCandidateRepository candidateRepository,
                         SectionRepository sectionRepository,
                         SubjectRepository subjectRepository,
                         FacultyRepository facultyRepository,
                         RoomRepository roomRepository,
                         TimetableRepository timetableRepository,
                         TimetableEntryRepository entryRepository,
                         AuditService auditService,
                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.extractor = extractor;
        this.ocrClient = ocrClient;
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
        this.sectionRepository = sectionRepository;
        this.subjectRepository = subjectRepository;
        this.facultyRepository = facultyRepository;
        this.roomRepository = roomRepository;
        this.timetableRepository = timetableRepository;
        this.entryRepository = entryRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------- upload

    @Transactional
    public ImportJobDto upload(MultipartFile file) {
        validateUpload(file);

        String storedName = UploadStorage.generateInternalName(PDF_EXTENSION);
        Path directory = Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
        Path stored;
        try {
            stored = UploadStorage.write(directory, storedName, file.getInputStream());
        } catch (IOException ex) {
            throw new PdfImportException("UPLOAD_FAILED", "The uploaded file could not be stored.");
        }

        ImportJob job = new ImportJob();
        job.setFilename(safeDisplayName(file.getOriginalFilename()));
        job.setStoredFilename(storedName);
        job.setStatus(ImportStatus.PENDING);
        job.setUploadedBy(CurrentUser.id());
        job.setUploadedByUsername(CurrentUser.username());
        job.setFileSizeBytes(file.getSize());
        ImportJob saved = jobRepository.save(job);

        auditService.recordAs(CurrentUser.id(), CurrentUser.username(), "IMPORT_UPLOAD", "ImportJob",
                saved.getId(), "Uploaded " + saved.getFilename());

        try {
            process(saved, stored);
        } catch (RuntimeException ex) {
            log.warn("Import {} failed", saved.getId(), ex);
            saved.setStatus(ImportStatus.FAILED);
            saved.setErrorSummary(truncate(ex.getMessage(), 1900));
            saved.setCompletedAt(Instant.now());
            return ImportJobDto.from(jobRepository.save(saved), 0, 0);
        }
        return get(saved.getId());
    }

    /** Runs extraction, OCR when needed, grid parsing and candidate generation. */
    private void process(ImportJob job, Path stored) {
        job.setStatus(ImportStatus.EXTRACTING);
        job.setStartedAt(Instant.now());
        jobRepository.save(job);

        PdfTextExtractor.ExtractionResult extraction = extractor.extract(stored);
        job.setTotalPages(extraction.pageCount());

        List<Integer> needsOcr = extraction.pagesNeedingOcr();
        job.setPagesNeedingOcr(needsOcr.size());

        Map<Integer, String> ocrText = Map.of();
        if (!needsOcr.isEmpty()) {
            job.setStatus(ImportStatus.OCR_PENDING);
            jobRepository.save(job);
            byte[] bytes = readAllBytes(stored);
            OcrClient.OcrResult result = ocrClient.recognise(bytes, needsOcr);
            Map<Integer, String> merged = new HashMap<>();
            for (OcrClient.OcrPage page : result.pages()) {
                merged.put(page.pageNumber(), page.text());
            }
            ocrText = merged;
            job.setOcrServiceUsed(true);
        }

        job.setStatus(ImportStatus.PARSING);
        jobRepository.save(job);

        List<ImportCandidate> candidates = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (PageText page : extraction.pages()) {
            String text = page.text();
            if (ocrText.containsKey(page.pageNumber())) {
                text = mergeText(text, ocrText.get(page.pageNumber()));
            }
            var parsed = gridParser.parse(new PageText(page.pageNumber(), text, page.words(),
                    page.sufficient() || ocrText.containsKey(page.pageNumber()), page.reason()));
            warnings.addAll(parsed.warnings());

            for (TimetableGridParser.Cell cell : parsed.cells()) {
                candidates.add(buildCandidate(job, page.pageNumber(), parsed.sectionLabel(), cell));
            }
        }

        candidateRepository.saveAll(candidates);

        job.setStatus(ImportStatus.AWAITING_REVIEW);
        job.setCompletedAt(Instant.now());
        job.setErrorSummary(warnings.isEmpty() ? null : truncate(String.join(" | ", warnings), 1900));
        jobRepository.save(job);
    }

    private ImportCandidate buildCandidate(ImportJob job, int pageNumber, String sectionLabel,
                                           TimetableGridParser.Cell cell) {
        CellContentParser.ParsedCell parsed = cellParser.parse(cell.rawText());

        ImportCandidate candidate = new ImportCandidate();
        candidate.setImportJob(job);
        candidate.setPageNumber(pageNumber);
        candidate.setRawText(truncate(cell.rawText(), 1900));
        candidate.setSubjectLabel(parsed.subjectName());
        candidate.setFacultyLabel(parsed.facultyName());
        candidate.setRoomLabel(parsed.roomCode());
        candidate.setDayOfWeek(cell.dayOfWeek());
        candidate.setStartTime(TimeText.parse(cell.startTime()));
        candidate.setEndTime(TimeText.parse(cell.endTime()));
        candidate.setEntryType(resolveEntryType(parsed.entryType()));
        candidate.setConfidence(BigDecimal.valueOf(Math.round(parsed.confidence() * 10000) / 10000.0));
        // A LinkedHashMap, because Map.of rejects null values and "field not found" is the
        // normal case here: writing the string "null" would misrepresent it as a real value.
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("sourcePage", pageNumber);
        fields.put("dayLabel", cell.dayLabel());
        fields.put("slot", cell.columnLabel());
        fields.put("startTime", cell.startTime());
        fields.put("endTime", cell.endTime());
        fields.put("subjectCode", parsed.subjectCode());
        fields.put("facultyCode", parsed.facultyCode());
        fields.put("roomCode", parsed.roomCode());
        fields.put("unresolved", parsed.unresolved());
        candidate.setExtractedFields(writeJson(fields));
        // The heading above the grid tells an administrator which section a row belongs to, which
        // matters for a document that carries a different section on every page.
        candidate.setSectionLabel(sectionLabel);
        candidate.setValidationErrors(parsed.unresolved().isEmpty()
                ? null : truncate(String.join("; ", parsed.unresolved()), 950));

        resolveReferences(candidate, parsed);
        return candidate;
    }

    /** Best effort match of the parsed labels to existing records. Null means "needs review". */
    private void resolveReferences(ImportCandidate candidate, CellContentParser.ParsedCell parsed) {
        if (parsed.subjectCode() != null) {
            subjectRepository.findBySubjectCodeIgnoreCase(parsed.subjectCode())
                    .ifPresent(subject -> candidate.setSubject(subject));
        }
        if (candidate.getSubject() == null && parsed.subjectName() != null) {
            String name = parsed.subjectName().toLowerCase(java.util.Locale.ROOT);
            subjectRepository.findAll().stream()
                    .filter(subject -> subject.getSubjectName().toLowerCase(java.util.Locale.ROOT).equals(name))
                    .findFirst()
                    .ifPresent(candidate::setSubject);
        }
        if (parsed.facultyCode() != null) {
            facultyRepository.findByFacultyCode(parsed.facultyCode())
                    .ifPresent(candidate::setFaculty);
        }
        if (candidate.getFaculty() == null && parsed.facultyName() != null) {
            facultyRepository.findByFacultyNameIgnoreCase(parsed.facultyName())
                    .ifPresent(candidate::setFaculty);
        }
        if (parsed.roomCode() != null) {
            roomRepository.findByRoomCodeIgnoreCase(parsed.roomCode())
                    .ifPresent(candidate::setRoom);
        }
    }

    // -------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public ImportJobDto get(Long jobId) {
        ImportJob job = requireJob(jobId);
        return ImportJobDto.from(job, candidateRepository.countByImportJobId(jobId),
                candidateRepository.countByImportJobIdAndReviewStatus(jobId,
                        com.college.timetable.entity.ReviewStatus.PENDING));
    }

    @Transactional(readOnly = true)
    public List<ImportJobDto> list() {
        return jobRepository.findByOrderByCreatedAtDesc().stream()
                .map(job -> ImportJobDto.from(job, candidateRepository.countByImportJobId(job.getId()),
                        candidateRepository.countByImportJobIdAndReviewStatus(job.getId(),
                                com.college.timetable.entity.ReviewStatus.PENDING)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ImportCandidateDto> candidates(Long jobId) {
        requireJob(jobId);
        return candidateRepository.findByImportJobIdOrderByPageNumberAscIdAsc(jobId).stream()
                .map(ImportCandidateDto::from).toList();
    }

    // ------------------------------------------------------------ updates

    @Transactional
    public ImportCandidateDto updateCandidate(Long jobId, Long candidateId,
                                              UpdateCandidateRequest request) {
        ImportJob job = requireJob(jobId);
        if (job.getStatus() == ImportStatus.APPROVED || job.getStatus() == ImportStatus.PUBLISHED) {
            throw ApiException.conflict("This import has already been approved and can no longer be edited.");
        }
        ImportCandidate candidate = candidateRepository.findById(candidateId)
                .filter(c -> c.getImportJob().getId().equals(jobId))
                .orElseThrow(() -> ApiException.notFound("Candidate " + candidateId + " was not found."));

        if (request.sectionId() != null) {
            Section section = sectionRepository.findById(request.sectionId())
                    .orElseThrow(() -> ApiException.badRequest("SECTION_NOT_FOUND",
                            "Section " + request.sectionId() + " does not exist."));
            candidate.setSection(section);
        }
        if (request.subjectId() != null) {
            Subject subject = subjectRepository.findById(request.subjectId())
                    .orElseThrow(() -> ApiException.badRequest("SUBJECT_NOT_FOUND",
                            "Subject " + request.subjectId() + " does not exist."));
            candidate.setSubject(subject);
        }
        if (request.facultyId() != null) {
            Faculty faculty = facultyRepository.findById(request.facultyId())
                    .orElseThrow(() -> ApiException.badRequest("FACULTY_NOT_FOUND",
                            "Faculty " + request.facultyId() + " does not exist."));
            candidate.setFaculty(faculty);
        }
        if (request.roomId() != null) {
            Room room = roomRepository.findById(request.roomId())
                    .orElseThrow(() -> ApiException.badRequest("ROOM_NOT_FOUND",
                            "Room " + request.roomId() + " does not exist."));
            candidate.setRoom(room);
        }
        if (request.sectionLabel() != null) {
            candidate.setSectionLabel(request.sectionLabel());
        }
        if (request.subjectLabel() != null) {
            candidate.setSubjectLabel(request.subjectLabel());
        }
        if (request.facultyLabel() != null) {
            candidate.setFacultyLabel(request.facultyLabel());
        }
        if (request.roomLabel() != null) {
            candidate.setRoomLabel(request.roomLabel());
        }
        if (request.dayOfWeek() != null) {
            candidate.setDayOfWeek(request.dayOfWeek());
        }
        if (request.startTime() != null) {
            candidate.setStartTime(requireTime(request.startTime(), "startTime"));
        }
        if (request.endTime() != null) {
            candidate.setEndTime(requireTime(request.endTime(), "endTime"));
        }
        if (request.entryType() != null) {
            candidate.setEntryType(parseEntryType(request.entryType()));
        }
        if (request.reviewerNotes() != null) {
            candidate.setReviewerNotes(request.reviewerNotes());
        }
        candidate.setReviewedBy(CurrentUser.id());

        if (request.reviewStatus() != null) {
            candidate.setReviewStatus(parseReviewStatus(request.reviewStatus()));
        } else if (candidate.getReviewStatus() == com.college.timetable.entity.ReviewStatus.PENDING) {
            candidate.setReviewStatus(com.college.timetable.entity.ReviewStatus.EDITED);
        }

        validateCandidate(candidate);
        ImportCandidate saved = candidateRepository.save(candidate);
        auditService.recordAs(CurrentUser.id(), CurrentUser.username(), "IMPORT_CANDIDATE_EDIT",
                "ImportCandidate", saved.getId(), "Job " + jobId);
        return ImportCandidateDto.from(saved);
    }

    @Transactional
    public void deleteCandidate(Long jobId, Long candidateId) {
        candidateRepository.findById(candidateId)
                .filter(c -> c.getImportJob().getId().equals(jobId))
                .orElseThrow(() -> ApiException.notFound("Candidate " + candidateId + " was not found."));
        candidateRepository.deleteById(candidateId);
        auditService.recordAs(CurrentUser.id(), CurrentUser.username(), "IMPORT_CANDIDATE_DELETE",
                "ImportCandidate", candidateId, "Job " + jobId);
    }

    // ------------------------------------------------------------ approve

    /**
     * Creates a DRAFT timetable from every approved or edited candidate. Runs in one transaction so
     * a failure never leaves a partially populated timetable behind.
     */
    @Transactional
    public Long approve(Long jobId, ApproveImportRequest request) {
        ImportJob job = requireJob(jobId);
        if (job.getStatus() == ImportStatus.PUBLISHED) {
            throw ApiException.conflict("This import has already been published.");
        }
        Section section = sectionRepository.findById(request.sectionId())
                .orElseThrow(() -> ApiException.badRequest("SECTION_NOT_FOUND",
                        "Choose the section these rows belong to."));
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw ApiException.badRequest("INVALID_EFFECTIVE_DATES",
                    "The effective end date must not be earlier than the start date.");
        }

        List<ImportCandidate> approved = new ArrayList<>(
                candidateRepository.findByImportJobIdAndReviewStatusOrderByPageNumberAscIdAsc(jobId,
                        com.college.timetable.entity.ReviewStatus.APPROVED));
        approved.addAll(candidateRepository.findByImportJobIdAndReviewStatusOrderByPageNumberAscIdAsc(jobId,
                com.college.timetable.entity.ReviewStatus.EDITED));

        if (approved.isEmpty()) {
            throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTHING_TO_APPROVE",
                    "No rows have been approved yet. Review the extracted rows first.");
        }

        Integer nextVersion = timetableRepository.findMaxVersion(section.getId());
        Timetable timetable = new Timetable();
        timetable.setSection(section);
        timetable.setAcademicTerm(section.getAcademicTerm());
        timetable.setEffectiveFrom(request.effectiveFrom());
        timetable.setEffectiveTo(request.effectiveTo());
        timetable.setStatus(TimetableStatus.DRAFT);
        timetable.setVersion(nextVersion == null ? 1 : nextVersion + 1);
        timetable.setSourceFilename(job.getFilename());
        timetable.setCreatedBy(CurrentUser.id());
        Timetable savedTimetable = timetableRepository.save(timetable);

        int created = 0;
        List<String> skipped = new ArrayList<>();
        for (ImportCandidate candidate : approved) {
            if (candidate.getDayOfWeek() == null || candidate.getStartTime() == null
                    || candidate.getEndTime() == null) {
                skipped.add("Candidate " + candidate.getId() + " has no day or time and was skipped.");
                continue;
            }
            if (!candidate.getEndTime().isAfter(candidate.getStartTime())) {
                skipped.add("Candidate " + candidate.getId() + " ends before it starts and was skipped.");
                continue;
            }
            TimetableEntry entry = new TimetableEntry();
            entry.setTimetable(savedTimetable);
            entry.setSection(section);
            entry.setSubject(candidate.getSubject());
            entry.setFaculty(candidate.getFaculty());
            entry.setRoom(candidate.getRoom());
            entry.setDayOfWeek(candidate.getDayOfWeek());
            entry.setStartTime(candidate.getStartTime());
            entry.setEndTime(candidate.getEndTime());
            entry.setEntryType(candidate.getEntryType() == null ? EntryType.CLASS : candidate.getEntryType());
            entry.setRawSourceText(candidate.getRawText());
            entry.setSourcePageNumber(candidate.getPageNumber());
            entry.setVerificationStatus(candidate.getReviewStatus()
                    == com.college.timetable.entity.ReviewStatus.EDITED
                    ? VerificationStatus.CORRECTED : VerificationStatus.VERIFIED);
            entryRepository.save(entry);
            created++;
        }

        if (created == 0) {
            throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTHING_IMPORTED", "None of the approved rows had a usable day and time.", skipped);
        }

        job.setStatus(ImportStatus.APPROVED);
        job.setResultTimetableId(savedTimetable.getId());
        job.setCompletedAt(Instant.now());
        if (!skipped.isEmpty()) {
            job.setErrorSummary(truncate("Created " + created + " periods. "
                    + String.join(" ", skipped), 1900));
        }
        jobRepository.save(job);

        auditService.recordAs(CurrentUser.id(), CurrentUser.username(), "IMPORT_APPROVE", "ImportJob",
                jobId, "Created draft timetable %d with %d periods".formatted(savedTimetable.getId(), created));
        return savedTimetable.getId();
    }

    // ----------------------------------------------------------- helpers

    /**
     * Rejects an upload before anything is written to disk.
     *
     * <p>Three independent checks, because any one of them can be bypassed: the file extension,
     * the declared content type, and the actual bytes. The magic byte check is the only one that
     * cannot be fooled by renaming a file, so a renamed file is refused here rather than turning
     * into a confusing FAILED import job later.
     */
    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PdfImportException("EMPTY_FILE", "Choose a PDF file to upload.");
        }
        if (!PDF_EXTENSION.equals(UploadStorage.safeExtension(file.getOriginalFilename()))) {
            throw new PdfImportException("NOT_A_PDF",
                    "Only PDF files are accepted. The uploaded file name must end with .pdf.");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.equalsIgnoreCase("application/pdf")
                && !contentType.equalsIgnoreCase("application/octet-stream")) {
            throw new PdfImportException("NOT_A_PDF", "The uploaded file is not a PDF.");
        }
        if (!looksLikePdf(file)) {
            throw new PdfImportException("NOT_A_PDF",
                    "The uploaded file is not a PDF, whatever its name says.");
        }
    }

    private static boolean looksLikePdf(MultipartFile file) {
        byte[] header = new byte[4];
        try (InputStream in = file.getInputStream()) {
            return in.read(header) == 4
                    && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F';
        } catch (IOException ex) {
            throw new PdfImportException("UPLOAD_FAILED", "The uploaded file could not be read.");
        }
    }

    private static String safeDisplayName(String original) {
        if (original == null || original.isBlank()) {
            return "timetable.pdf";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\t]", " ");
        return name.length() > 250 ? name.substring(0, 250) : name;
    }

    private static String mergeText(String embedded, String ocr) {
        if (embedded == null || embedded.isBlank()) {
            return ocr;
        }
        if (ocr == null || ocr.isBlank()) {
            return embedded;
        }
        return embedded.strip() + "\n" + ocr.strip();
    }

    private void validateCandidate(ImportCandidate candidate) {
        List<String> errors = new ArrayList<>();
        if (candidate.getDayOfWeek() == null) {
            errors.add("A weekday is required.");
        } else if (candidate.getDayOfWeek() < 1 || candidate.getDayOfWeek() > 7) {
            errors.add("The weekday must be between 1 (Monday) and 7 (Sunday).");
        }
        if (candidate.getStartTime() == null) {
            errors.add("A start time is required.");
        }
        if (candidate.getEndTime() == null) {
            errors.add("An end time is required.");
        }
        if (candidate.getStartTime() != null && candidate.getEndTime() != null
                && !candidate.getEndTime().isAfter(candidate.getStartTime())) {
            errors.add("The end time must be later than the start time.");
        }
        if (candidate.getEntryType() == EntryType.CLASS && candidate.getSubject() == null) {
            errors.add("A class row must be mapped to an existing subject.");
        }
        candidate.setValidationErrors(errors.isEmpty() ? null : truncate(String.join("; ", errors), 950));
    }

    private static java.time.LocalTime requireTime(String value, String field) {
        java.time.LocalTime time = TimeText.parse(value);
        if (time == null) {
            throw ApiException.badRequest("INVALID_TIME", field + " is not a valid time, for example 09:30.");
        }
        return time;
    }

    private static EntryType resolveEntryType(CellContentParser.EntryTypeHint hint) {
        return switch (hint) {
            case CLASS -> EntryType.CLASS;
            case BREAK -> EntryType.BREAK;
            case OTHER -> EntryType.OTHER;
        };
    }

    private static EntryType parseEntryType(String value) {
        try {
            return EntryType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_ENTRY_TYPE", "entryType must be CLASS, BREAK or OTHER.");
        }
    }

    private static com.college.timetable.entity.ReviewStatus parseReviewStatus(String value) {
        try {
            return com.college.timetable.entity.ReviewStatus.valueOf(
                    value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_REVIEW_STATUS",
                    "reviewStatus must be PENDING, APPROVED, EDITED or REJECTED.");
        }
    }

    private static byte[] readAllBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new PdfImportException("UPLOAD_FAILED", "The stored file could not be read back.");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return "{}";
        }
    }

    private ImportJob requireJob(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Import job " + id + " was not found."));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}