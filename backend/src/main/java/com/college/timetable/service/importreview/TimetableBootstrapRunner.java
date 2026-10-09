package com.college.timetable.service.importreview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.college.timetable.config.ImportProperties;
import com.college.timetable.entity.AcademicTerm;
import com.college.timetable.entity.Department;
import com.college.timetable.entity.EntryType;
import com.college.timetable.entity.Faculty;
import com.college.timetable.entity.Program;
import com.college.timetable.entity.Room;
import com.college.timetable.entity.Section;
import com.college.timetable.entity.Subject;
import com.college.timetable.entity.Timetable;
import com.college.timetable.entity.TimetableEntry;
import com.college.timetable.entity.TimetableStatus;
import com.college.timetable.entity.VerificationStatus;
import com.college.timetable.repository.AcademicTermRepository;
import com.college.timetable.repository.DepartmentRepository;
import com.college.timetable.repository.FacultyRepository;
import com.college.timetable.repository.ProgramRepository;
import com.college.timetable.repository.RoomRepository;
import com.college.timetable.repository.SectionRepository;
import com.college.timetable.repository.SubjectRepository;
import com.college.timetable.repository.TimetableEntryRepository;
import com.college.timetable.service.audit.AuditService;
import com.college.timetable.repository.TimetableRepository;
import com.college.timetable.service.pdf.CellContentParser;
import com.college.timetable.service.pdf.PageText;
import com.college.timetable.service.pdf.PdfTextExtractor;
import com.college.timetable.service.pdf.TimetableGridParser;
import com.college.timetable.util.SectionLabelParser;
import com.college.timetable.util.TimeText;

/**
 * Imports a whole published timetable document, one section per page.
 *
 * <p>Built for documents like a college's "section wise timetable", where every page is one
 * section and carries its own heading. It performs the same reading pipeline as the interactive
 * import, and then:
 *
 * <ol>
 *   <li>derives the catalogue (programme, semester, section) from the heading printed on each page;</li>
 *   <li>discards repeated page furniture, such as a timetable website watermark, by noticing text
 *       that appears on an implausibly large share of pages;</li>
 *   <li>resolves subjects, faculty and rooms to database records, creating the ones the document
 *       introduces;</li>
 *   <li>creates one draft timetable per section and, when asked to, publishes it.</li>
 * </ol>
 *
 * <p>Rows that cannot be resolved are counted and printed rather than guessed at. The run is
 * idempotent per section: a section that already has a published timetable is left alone unless
 * republishing was explicitly requested.
 */
@Component
@ConditionalOnProperty(name = "college.import.bootstrap.enabled", havingValue = "true")
public class TimetableBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TimetableBootstrapRunner.class);

    /**
     * Text appearing on more than this share of pages is treated as page furniture, not schedule.
     * A real activity such as a library slot appears in a minority of sections; a watermark or a
     * "generated on" footer appears on all of them.
     */
    private static final double FURNITURE_PAGE_SHARE = 0.20;

    private final ImportProperties properties;
    private final PdfTextExtractor extractor;
    private final TimetableGridParser gridParser = TimetableGridParser.standard();
    private final CellContentParser cellParser = new CellContentParser();

    private final DepartmentRepository departments;
    private final ProgramRepository programs;
    private final AcademicTermRepository terms;
    private final SectionRepository sections;
    private final SubjectRepository subjects;
    private final FacultyRepository faculties;
    private final RoomRepository rooms;
    private final TimetableRepository timetables;
    private final TimetableEntryRepository entries;
    private final AuditService auditService;

    public TimetableBootstrapRunner(ImportProperties properties,
                                    PdfTextExtractor extractor,
                                    DepartmentRepository departments,
                                    ProgramRepository programs,
                                    AcademicTermRepository terms,
                                    SectionRepository sections,
                                    SubjectRepository subjects,
                                    FacultyRepository faculties,
                                    RoomRepository rooms,
                                    TimetableRepository timetables,
                                    TimetableEntryRepository entries,
                                    AuditService auditService) {
        this.properties = properties;
        this.extractor = extractor;
        this.departments = departments;
        this.programs = programs;
        this.terms = terms;
        this.sections = sections;
        this.subjects = subjects;
        this.faculties = faculties;
        this.rooms = rooms;
        this.timetables = timetables;
        this.entries = entries;
        this.auditService = auditService;
    }

    /** One period read off the page, before it is turned into a database row. */
    record Row(int page, String sectionLabel, int dayOfWeek, String startTime, String endTime,
                       String subjectLabel, String subjectCode, String facultyLabel, String facultyCode,
                       String roomCode, String rawText) {
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        BootstrapConfig config = readConfig();
        Path source = Path.of(config.source()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            log.error("Timetable document not found: {}", source);
            return;
        }

        List<PageText> pages;
        try {
            pages = extractor.extract(source).pages();
        } catch (RuntimeException ex) {
            log.error("Could not read {}: {}", source, ex.getMessage());
            return;
        }
        if (pages.stream().anyMatch(page -> !page.sufficient())) {
            log.warn("Some pages have no extractable text. They will be reported and skipped.");
        }

        // --- read every page into rows -----------------------------------
        Map<String, List<Row>> rowsBySection = new LinkedHashMap<>();
        Map<String, Set<Integer>> pagesByLabel = new LinkedHashMap<>();
        Map<String, Integer> labelFrequency = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        List<String> unreadableSections = new ArrayList<>();

        for (PageText page : pages) {
            var parsed = gridParser.parse(page);
            warnings.addAll(parsed.warnings());
            String label = parsed.sectionLabel();
            if (label == null || SectionLabelParser.parse(label) == null) {
                unreadableSections.add("page " + page.pageNumber());
                continue;
            }
            pagesByLabel.computeIfAbsent(label, key -> new TreeSet<>()).add(page.pageNumber());
            List<Row> rows = rowsBySection.computeIfAbsent(label, key -> new ArrayList<>());
            for (TimetableGridParser.Cell cell : parsed.cells()) {
                var content = cellParser.parse(cell.rawText());
                rows.add(new Row(page.pageNumber(), label, cell.dayOfWeek(), cell.startTime(),
                        cell.endTime(), content.subjectName(), content.subjectCode(),
                        content.facultyName(), content.facultyCode(), content.roomCode(), cell.rawText()));
            }
        }

        // --- drop repeated page furniture ---------------------------------
        int pageCount = Math.max(1, pages.size());
        int furnitureLimit = (int) Math.ceil(pageCount * FURNITURE_PAGE_SHARE);
        Map<String, Integer> labelOccurrences = new HashMap<>();
        for (List<Row> rows : rowsBySection.values()) {
            for (Row row : rows) {
                String key = normaliseKey(row.subjectLabel());
                labelOccurrences.merge(key, 1, Integer::sum);
            }
        }

        int droppedFurniture = 0;
        int keptClasses = 0;
        int keptActivities = 0;
        int unresolved = 0;
        List<String> skippedExamples = new ArrayList<>();

        for (Map.Entry<String, List<Row>> entry : rowsBySection.entrySet()) {
            List<Row> kept = new ArrayList<>();
            for (Row row : entry.getValue()) {
                Row cleaned = stripFurniture(row);
                boolean furniture = cleaned.subjectCode() == null
                        && labelOccurrences.getOrDefault(normaliseKey(cleaned.subjectLabel()), 0)
                        > furnitureLimit;
                if (furniture) {
                    droppedFurniture++;
                    continue;
                }
                if (!cleaned.rawText().equals(row.rawText())) {
                    droppedFurniture++;
                }
                kept.add(cleaned);
            }
            entry.setValue(kept);
            for (Row row : kept) {
                if (row.subjectCode() != null) {
                    keptClasses++;
                } else if (row.subjectLabel() != null && !row.subjectLabel().isBlank()) {
                    keptActivities++;
                    if (keptActivities <= 15) {
                        skippedExamples.add(row.sectionLabel() + " " + row.startTime()
                                + " " + row.subjectLabel() + " (no subject code)");
                    }
                } else {
                    unresolved++;
                }
            }
        }

        // --- catalogue ----------------------------------------------------
        AcademicTerm term = resolveAcademicTerm(config);
        Map<String, Program> programCache = new HashMap<>();
        Map<String, Subject> subjectCache = new HashMap<>();
        Map<String, Faculty> facultyCache = new HashMap<>();
        Map<String, Room> roomCache = new HashMap<>();

        List<String> sectionReports = new ArrayList<>();
        int published = 0;
        int skippedExisting = 0;
        int withValidationIssues = 0;

        for (Map.Entry<String, List<Row>> entry : rowsBySection.entrySet()) {
            String label = entry.getKey();
            var parsedLabel = SectionLabelParser.parse(label);
            if (parsedLabel == null) {
                continue;
            }
            Section section = resolveSection(parsedLabel, programCache, term);
            List<Row> rows = entry.getValue();
            if (rows.isEmpty()) {
                sectionReports.add(String.format("%-28s 0 periods", label));
                continue;
            }

            var existing = timetables.findBySectionAndStatus(section.getId(), TimetableStatus.PUBLISHED);
            if (existing.stream().anyMatch(t -> t.getStatus() == TimetableStatus.PUBLISHED) && !config.republish()) {
                skippedExisting++;
                sectionReports.add(String.format("%-28s already published, left alone", label));
                continue;
            }

            Timetable draft = createDraft(section, term, config, label);
            int added = 0;
            for (Row row : rows) {
                TimetableEntry timetableEntry = new TimetableEntry();
                timetableEntry.setTimetable(draft);
                timetableEntry.setSection(section);
                timetableEntry.setDayOfWeek(row.dayOfWeek());
                timetableEntry.setStartTime(TimeText.parse(row.startTime()));
                timetableEntry.setEndTime(TimeText.parse(row.endTime()));
                timetableEntry.setEntryType(row.subjectCode() != null ? EntryType.CLASS : EntryType.OTHER);
                timetableEntry.setSubject(resolveSubject(row, section.getProgram(), subjectCache));
                timetableEntry.setFaculty(resolveFaculty(row, facultyCache));
                timetableEntry.setRoom(resolveRoom(row, roomCache));
                timetableEntry.setRawSourceText(truncate(row.rawText(), 900));
                timetableEntry.setSourcePageNumber(row.page());
                // A row that resolved to a subject was read correctly; one that did not is kept
                // but flagged as unverified so it stands out when an administrator reviews it.
                timetableEntry.setVerificationStatus(row.subjectCode() != null
                        ? VerificationStatus.VERIFIED : VerificationStatus.UNVERIFIED);
                entries.save(timetableEntry);
                added++;
            }

            var report = validateConflicts(draft);
            if (!report.isEmpty()) {
                withValidationIssues++;
                sectionReports.add(String.format("%-28s %2d periods, %d overlap issue(s): %s",
                        label, added, report.size(), truncate(String.join("; ", report), 160)));
            } else {
                sectionReports.add(String.format("%-28s %2d periods", label, added));
            }

            if (config.publish()) {
                // Overlapping periods would make "what is scheduled now" ambiguous for every
                // student in the section, so a conflicted draft is left unpublished for a person
                // to resolve rather than pushed live with only a console warning.
                if (!report.isEmpty()) {
                    continue;
                }
                draft.setStatus(TimetableStatus.PUBLISHED);
                draft.setPublishedAt(Instant.now());
                draft.setPublishedBy(null);
                timetables.save(draft);
                published++;
                // Publishing a whole document is a significant administrative act, so it is
                // recorded in the same trail as changes made through the screens.
                auditService.record("TIMETABLE_BOOTSTRAP_PUBLISH", "Timetable", draft.getId(),
                        section.getProgram().getCode() + " section " + section.getSectionName()
                                + ", " + added + " periods from " + source.getFileName());
            }
        }

        printReport(source, pages, pagesByLabel, unreadableSections, warnings, droppedFurniture,
                keptClasses, keptActivities, unresolved, skippedExamples, published, skippedExisting,
                withValidationIssues, sectionReports, config);
    }

    // ------------------------------------------------------------- helpers

    private record BootstrapConfig(String source, String academicYear,
                                   java.time.LocalDate effectiveFrom, java.time.LocalDate effectiveTo,
                                   boolean publish, boolean republish) {
    }

    private BootstrapConfig readConfig() {
        var bootstrap = properties.getBootstrap();
        return new BootstrapConfig(bootstrap.getSource(), bootstrap.getAcademicYear(),
                bootstrap.getEffectiveFrom(), bootstrap.getEffectiveTo(),
                bootstrap.isPublish(), bootstrap.isRepublish());
    }

    private AcademicTerm resolveAcademicTerm(BootstrapConfig config) {
        return terms.findByAcademicYearAndSemesterNumber(config.academicYear(), 5)
                .orElseGet(() -> {
                    AcademicTerm term = new AcademicTerm();
                    term.setAcademicYear(config.academicYear());
                    term.setSemesterNumber(5);
                    term.setStartDate(config.effectiveFrom());
                    term.setEndDate(config.effectiveTo());
                    term.setActive(true);
                    AcademicTerm saved = terms.save(term);
                    log.info("Created academic term {} semester 5 covering {} to {}",
                            config.academicYear(), saved.getStartDate(), saved.getEndDate());
                    return saved;
                });
    }

    private Section resolveSection(SectionLabelParser.ParsedSection parsed, Map<String, Program> cache,
                                   AcademicTerm baseTerm) {
        String programCode = SectionLabelParser.programCode(parsed.program());
        Program program = cache.computeIfAbsent(programCode, code -> programs.findByCodeIgnoreCase(code)
                .orElseGet(() -> {
                    Program created = new Program();
                    Department department = resolveDepartment(code);
                    created.setDepartment(department);
                    created.setCode(code);
                    created.setName(prettyProgramme(parsed.program()));
                    created.setDegreeType(code.startsWith("BTC") ? "UG"
                            : code.startsWith("MT") ? "PG" : "UG");
                    created.setActive(true);
                    log.info("Created programme {} ({})", created.getName(), code);
                    return programs.save(created);
                }));

        AcademicTerm term = terms.findByAcademicYearAndSemesterNumber(baseTerm.getAcademicYear(),
                        parsed.semester())
                .orElseGet(() -> {
                    AcademicTerm created = new AcademicTerm();
                    created.setAcademicYear(baseTerm.getAcademicYear());
                    created.setSemesterNumber(parsed.semester());
                    created.setStartDate(baseTerm.getStartDate());
                    created.setEndDate(baseTerm.getEndDate());
                    created.setActive(true);
                    return terms.save(created);
                });

        return sections.findByProgramIdAndAcademicTermIdAndSectionNameIgnoreCase(program.getId(),
                        term.getId(), parsed.section())
                .orElseGet(() -> {
                    Section created = new Section();
                    created.setProgram(program);
                    created.setAcademicTerm(term);
                    created.setSectionName(parsed.section());
                    created.setActive(true);
                    return sections.save(created);
                });
    }

    private Department resolveDepartment(String programCode) {
        if (programCode.startsWith("BTC")) {
            return departments.findByCodeIgnoreCase("CSE")
                    .orElseGet(() -> saveDepartment("CSE", "Computer Science and Engineering"));
        }
        if (programCode.startsWith("MT")) {
            return departments.findByCodeIgnoreCase("MTECH")
                    .orElseGet(() -> saveDepartment("MTECH", "Master of Technology"));
        }
        if (programCode.startsWith("MCA")) {
            return departments.findByCodeIgnoreCase("MCA")
                    .orElseGet(() -> saveDepartment("MCA", "Master of Computer Applications"));
        }
        return departments.findByCodeIgnoreCase("BCA")
                .orElseGet(() -> saveDepartment("BCA", "Bachelor of Computer Applications"));
    }

    private Department saveDepartment(String code, String name) {
        Department department = new Department();
        department.setCode(code);
        department.setName(name);
        department.setActive(true);
        log.info("Created department {} ({})", name, code);
        return departments.save(department);
    }

    private Timetable createDraft(Section section, AcademicTerm term, BootstrapConfig config, String label) {
        Timetable timetable = new Timetable();
        timetable.setSection(section);
        timetable.setAcademicTerm(term);
        timetable.setEffectiveFrom(config.effectiveFrom());
        timetable.setEffectiveTo(config.effectiveTo());
        timetable.setStatus(TimetableStatus.DRAFT);
        timetable.setSourceFilename("Section wise Timetable V15.pdf [" + label + "]");
        Integer next = timetables.findMaxVersion(section.getId());
        timetable.setVersion(next == null ? 1 : next + 1);
        return timetables.save(timetable);
    }

    private Subject resolveSubject(Row row, Program program, Map<String, Subject> cache) {
        if (row.subjectCode() == null) {
            return null;
        }
        return cache.computeIfAbsent(row.subjectCode(), code ->
                subjects.findBySubjectCodeIgnoreCase(code).orElseGet(() -> {
                    Subject created = new Subject();
                    created.setSubjectCode(code);
                    created.setSubjectName(trimSubjectName(row.subjectLabel()));
                    created.setProgram(program);
                    created.setActive(true);
                    return subjects.save(created);
                }));
    }

    private Faculty resolveFaculty(Row row, Map<String, Faculty> cache) {
        if (row.facultyCode() == null) {
            return null;
        }
        String code = row.facultyCode();
        return cache.computeIfAbsent(code, key -> faculties.findByFacultyCode(key).orElseGet(() -> {
            Faculty created = new Faculty();
            created.setFacultyCode(key);
            created.setFacultyName(trimFacultyName(row.facultyLabel()));
            created.setActive(true);
            return faculties.save(created);
        }));
    }

    private Room resolveRoom(Row row, Map<String, Room> cache) {
        if (row.roomCode() == null) {
            return null;
        }
        String code = row.roomCode();
        return cache.computeIfAbsent(code, key -> rooms.findByRoomCodeIgnoreCase(key).orElseGet(() -> {
            Room created = new Room();
            created.setRoomCode(key);
            String[] parts = key.split("-");
            if (parts.length >= 2) {
                created.setBuilding("Block " + parts[0]);
                if (parts.length >= 3) {
                    created.setFloor(parts[1]);
                }
            }
            return rooms.save(created);
        }));
    }

    /** Overlapping periods are reported rather than resolved, matching the lookup engine. */
    private List<String> validateConflicts(Timetable timetable) {
        List<String> issues = new ArrayList<>();
        for (List<TimetableEntry> day : entries.findByTimetableIdOrderByDayOfWeekAscStartTimeAsc(
                timetable.getId()).stream().collect(
                java.util.stream.Collectors.groupingBy(TimetableEntry::getDayOfWeek)).values()) {
            List<TimetableEntry> sorted = day.stream()
                    .sorted(Comparator.comparing(TimetableEntry::getStartTime)).toList();
            for (int i = 1; i < sorted.size(); i++) {
                TimetableEntry previous = sorted.get(i - 1);
                TimetableEntry current = sorted.get(i);
                if (current.getStartTime().isBefore(previous.getEndTime())) {
                    issues.add("day " + current.getDayOfWeek() + " " + current.getStartTime()
                            + "-" + current.getEndTime() + " overlaps " + previous.getStartTime()
                            + "-" + previous.getEndTime());
                }
            }
        }
        return issues;
    }

    /**
     * Removes the footers and watermarks a timetable tool prints on every page.
     *
     * <p>These sit below the last row of the grid, so PDFBox puts them in the same vertical band as
     * the final cell of the day and the cell text comes out as "Data Analytics generated: 1/9/2026".
     * Dropping the whole cell would lose a real class, so the marker is cut out instead. Only exact
     * known markers are removed: guessing at arbitrary trailing words would quietly truncate real
     * subject names.
     */
private static final java.util.regex.Pattern[] FURNITURE_MARKERS = {
            java.util.regex.Pattern.compile("(?i)\\s*aSc\\s*Timetables?\\s*Online\\s*"),
            java.util.regex.Pattern.compile("(?i)\\s*aSc\\s*"),
            java.util.regex.Pattern.compile("(?i)\\s*generated\\s*:\\s*\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}\\s*"),
            java.util.regex.Pattern.compile("(?i)\\s*generated\\s*on\\s*[:\\d/\\- ]*"),
            java.util.regex.Pattern.compile("(?i)\\s*page\\s*\\d+\\s*(of|/)\\s*\\d+\\s*"),
    };

    static Row stripFurniture(Row row) {
        // Each field is cleaned on its own. A cell can carry a footer even when it has no subject
        // label - "MINOR_CSE14050 aSc Timetables Online" is one - so returning early when the
        // label is absent would leave the marker in the stored text.
        String label = removeMarkers(row.subjectLabel());
        String raw = removeMarkers(row.rawText());
        if (java.util.Objects.equals(label, row.subjectLabel())
                && java.util.Objects.equals(raw, row.rawText())) {
            return row;
        }
        return new Row(row.page(), row.sectionLabel(), row.dayOfWeek(), row.startTime(),
                row.endTime(), label == null || label.isEmpty() ? null : label, row.subjectCode(),
                row.facultyLabel(), row.facultyCode(), row.roomCode(), raw);
    }

    private static String removeMarkers(String text) {
        if (text == null) {
            return null;
        }
        String result = text;
        for (java.util.regex.Pattern marker : FURNITURE_MARKERS) {
            result = marker.matcher(result).replaceAll(" ");
        }
        return result.replaceAll("\\s+", " ").strip();
    }

    private static String normaliseKey(String text) {
        return (text == null ? "" : text).replaceAll("\\s+", " ").strip().toUpperCase(Locale.ROOT);
    }

    private static String trimSubjectName(String label) {
        if (label == null || label.isBlank()) {
            return "Unnamed subject";
        }
        String cleaned = label.replaceAll("\\s+", " ").strip();
        return truncate(cleaned, 190);
    }

    private static String trimFacultyName(String label) {
        if (label == null || label.isBlank()) {
            return "Unnamed faculty";
        }
        return truncate(label.replaceAll("\\s+", " ").strip(), 140);
    }

    /** "B.TECH CSE" reads badly as a programme name; "B.Tech CSE" is the same text, set properly. */
    private static String prettyProgramme(String raw) {
        String[] words = raw.replaceAll("\\s+", " ").trim().split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            if (word.contains(".")) {
                for (String part : word.split("\\.")) {
                    if (!part.isEmpty()) {
                        if (builder.length() > 0 && !builder.toString().endsWith(" ")) {
                            builder.append('.');
                        }
                        builder.append(capitalise(part));
                    }
                }
            } else {
                builder.append(capitalise(word));
            }
        }
        return builder.toString().trim();
    }

    /**
     * Degree and discipline abbreviations that must stay upper case. These are printed that way in
     * the document and collapsing them to "Cse" or "Dsdt" makes the catalogue harder to read.
     */
    private static final java.util.Set<String> ACRONYMS = java.util.Set.of(
            "B.TECH", "MTECH", "M.TECH", "CSE", "BCA", "MCA", "DSDT", "ECE", "ME", "CE", "EE", "CIVIL",
            "AI", "ML", "AIML", "AI&ML", "DS", "CYBER", "SECURITY", "CLOUD", "IOT", "SEM");

    private static String capitalise(String word) {
        if (word.isEmpty()) {
            return word;
        }
        String upper = word.toUpperCase(Locale.ROOT);
        if (ACRONYMS.contains(upper)) {
            return upper;
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private void printReport(Path source, List<PageText> pages, Map<String, Set<Integer>> pagesByLabel,
                             List<String> unreadable, List<String> warnings, int droppedFurniture,
                             int classes, int activities, int unresolved, List<String> skippedExamples,
                             int published, int skippedExisting, int withIssues,
                             List<String> sectionReports, BootstrapConfig config) {
        StringBuilder out = new StringBuilder();
        out.append(System.lineSeparator());
        out.append("================ TIMETABLE IMPORT REPORT ================").append(System.lineSeparator());
        out.append("Document          : ").append(source.getFileName()).append(System.lineSeparator());
        out.append("Pages             : ").append(pages.size())
                .append("  (").append(pages.stream().filter(PageText::sufficient).count())
                .append(" with extractable text)").append(System.lineSeparator());
        out.append("Sections found    : ").append(pagesByLabel.size()).append(System.lineSeparator());
        out.append("Effective from    : ").append(config.effectiveFrom())
                .append("  to  ").append(config.effectiveTo()).append(System.lineSeparator());
        out.append(System.lineSeparator());
        out.append("Periods with a subject code (CLASS) : ").append(classes).append(System.lineSeparator());
        out.append("Periods without a code (OTHER)     : ").append(activities).append(System.lineSeparator());
        out.append("Page furniture rows discarded     : ").append(droppedFurniture).append(System.lineSeparator());
        out.append("Rows with nothing readable        : ").append(unresolved).append(System.lineSeparator());
        out.append("Timetables published              : ").append(published)
                .append("   (skipped, already published: ").append(skippedExisting).append(")").append(System.lineSeparator());
        out.append("Sections with overlap warnings    : ").append(withIssues).append(System.lineSeparator());
        out.append(System.lineSeparator());

        if (!unreadable.isEmpty()) {
            out.append("Pages with no readable section heading (").append(unreadable.size()).append("): ")
                    .append(truncate(String.join(", ", unreadable), 200)).append(System.lineSeparator())
                    .append(System.lineSeparator());
        }

        out.append("Sample periods imported as OTHER (no subject code, review these):").append(System.lineSeparator());
        for (String example : skippedExamples) {
            out.append("   ").append(example).append(System.lineSeparator());
        }

        if (!warnings.isEmpty()) {
            out.append(System.lineSeparator()).append("Parser warnings:").append(System.lineSeparator());
            warnings.stream().distinct().limit(20).forEach(w -> out.append("   ").append(w).append(System.lineSeparator()));
        }

        out.append(System.lineSeparator()).append("Per section:").append(System.lineSeparator());
        sectionReports.forEach(line -> out.append("   ").append(line).append(System.lineSeparator()));
        out.append("========================================================").append(System.lineSeparator());

        log.info("\n{}", out);
        System.out.println(out);
    }
}