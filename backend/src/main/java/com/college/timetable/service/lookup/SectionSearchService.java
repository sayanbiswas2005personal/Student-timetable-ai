package com.college.timetable.service.lookup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.college.timetable.config.SearchProperties;
import com.college.timetable.dto.lookup.AmbiguityOption;
import com.college.timetable.entity.Program;
import com.college.timetable.entity.Section;
import com.college.timetable.repository.ProgramRepository;
import com.college.timetable.repository.SectionRepository;
import com.college.timetable.util.SearchText;

/**
 * Resolves a course / semester / section search into verified {@link Section} rows.
 *
 * <p>Every candidate must already exist in the database. When the input still matches more than
 * one section the resolver says so instead of choosing, because picking the wrong section would
 * answer with the wrong student's timetable.
 */
@Service
public class SectionSearchService {

    private final ProgramRepository programRepository;
    private final SectionRepository sectionRepository;
    private final SearchProperties searchProperties;

    public SectionSearchService(ProgramRepository programRepository,
                                SectionRepository sectionRepository,
                                SearchProperties searchProperties) {
        this.programRepository = programRepository;
        this.sectionRepository = sectionRepository;
        this.searchProperties = searchProperties;
    }

    public enum Outcome {
        /** Exactly one verified section matched. */
        MATCHED,
        /** Several sections matched; the caller must let the user choose. */
        AMBIGUOUS,
        /** Nothing matched. */
        NOT_FOUND
    }

    /**
     * A resolved section, already turned into its response form.
     *
     * <p>The DTO is built here, inside the transaction that loaded the section. A section holds
     * lazy references to its programme and department; once that transaction closes they can no
     * longer be read, and a later attempt would fail at runtime.
     */
    public record ResolvedSection(Long sectionId, AmbiguityOption option) {
    }

    public record Resolution(Outcome outcome, List<ResolvedSection> sections, String explanation) {

        public ResolvedSection single() {
            return sections.size() == 1 ? sections.get(0) : null;
        }
    }

    /**
     * Resolves a section the caller already knows the id of.
     *
     * <p>Kept separate from {@link #resolve} because that method's first argument is a programme
     * id, and passing a section id there would silently search the wrong programme.
     */
    @Transactional(readOnly = true)
    public Resolution resolveById(Long sectionId) {
        Section section = sectionRepository.findById(sectionId).orElse(null);
        if (section == null || !section.isActive()) {
            return new Resolution(Outcome.NOT_FOUND, List.of(), "Section " + sectionId + " was not found.");
        }
        ResolvedSection resolved = new ResolvedSection(section.getId(),
                StudentLookupService.toOption(section));
        return new Resolution(Outcome.MATCHED, List.of(resolved), null);
    }

    /**
     * Deterministic structured search.
     *
     * @param programId     optional explicit programme, already chosen by the user
     * @param semesterNumber optional semester number 1..12
     * @param sectionName   optional section label, only honoured when the user labelled it
     * @param academicYear  optional academic year filter
     * @param freeText      optional natural language remainder, used to resolve the programme
     */
    @Transactional(readOnly = true)
    public Resolution resolve(Long programId,
                              Integer semesterNumber,
                              String sectionName,
                              String academicYear,
                              String freeText) {
        SearchQueryParser.ParsedQuery parsed = SearchQueryParser.parse(freeText, null);

        Integer semester = semesterNumber != null ? semesterNumber : parsed.semesterNumber();
        String year = academicYear != null ? academicYear : parsed.academicYear();
        String section = sectionName != null ? sectionName : parsed.sectionLabel();

        // "section: all" and friends mean "do not filter", never a section literally named "all".
        Set<String> nonSection = searchProperties.getNonSectionLabels();
        if (section != null && nonSection.contains(section.toLowerCase(Locale.ROOT))) {
            section = null;
        }

        List<Program> programs = programId != null
                ? programRepository.findById(programId).map(List::of).orElse(List.of())
                : resolvePrograms(freeText, parsed);

        if (programs.isEmpty()) {
            return new Resolution(Outcome.NOT_FOUND, List.of(),
                    freeText == null || freeText.isBlank()
                            ? "Enter a course, semester or section to search."
                            : "No course matches that description in the verified course list.");
        }

        List<Section> matches = new ArrayList<>();
        for (Program program : programs) {
            for (Section candidate : sectionRepository.findActiveByProgram(program.getId())) {
                if (matchesSection(candidate, semester, year, section)) {
                    matches.add(candidate);
                }
            }
        }

        if (matches.isEmpty()) {
            return new Resolution(Outcome.NOT_FOUND, List.of(),
                    "No section matches that course, semester and section combination.");
        }
        List<Section> limited = matches.size() > searchProperties.getMaxSuggestions()
                ? matches.subList(0, searchProperties.getMaxSuggestions()) : matches;
        List<ResolvedSection> resolved = limited.stream()
                .map(match -> new ResolvedSection(match.getId(), StudentLookupService.toOption(match)))
                .toList();

        if (matches.size() == 1) {
            return new Resolution(Outcome.MATCHED, resolved, null);
        }
        return new Resolution(Outcome.AMBIGUOUS, resolved,
                "That search matches %d sections. Please choose one.".formatted(matches.size()));
    }

    private boolean matchesSection(Section candidate, Integer semester, String academicYear, String section) {
        var term = candidate.getAcademicTerm();
        if (semester != null && term.getSemesterNumber() != semester) {
            return false;
        }
        if (academicYear != null && !academicYear.equalsIgnoreCase(term.getAcademicYear())) {
            return false;
        }
        if (section != null && !sectionNameMatches(candidate.getSectionName(), section)) {
            return false;
        }
        return true;
    }

    /**
     * Compares a typed section against the stored name.
     *
     * <p>Colleges qualify some section names: this document prints "G (AIML)", "L -SAP" and
     * "B (BFSI)" as separate sections from the plain "G". Somebody looking for the AIML section
     * types "G", and answering "no section matches" would be wrong. So an exact match always
     * counts, and otherwise the typed value is compared against the leading letter group.
     */
    private boolean sectionNameMatches(String storedName, String typed) {
        if (storedName == null || typed == null) {
            return false;
        }
        String stored = storedName.strip();
        if (stored.equalsIgnoreCase(typed.strip())) {
            return true;
        }
        String head = LEADING_SECTION_LETTERS.matcher(stored).results()
                .map(java.util.regex.MatchResult::group)
                .findFirst()
                .orElse(null);
        return head != null && head.equalsIgnoreCase(typed.strip());
    }

    /** The leading run of letters in a section name, so "G (AIML)" yields "G". */
    private static final java.util.regex.Pattern LEADING_SECTION_LETTERS =
            java.util.regex.Pattern.compile("^[A-Za-z]+");

    /**
     * Resolves the programme part of the query against the verified programme list and the
     * administrator declared aliases.
     *
     * <p>Matching happens in memory over the full active programme list because punctuation and
     * spacing differ so much between how a programme is stored and how a person types it
     * ("B.Tech CSE AI-ML" versus "btech cse ai ml"). The list is small and read-only.
     */
    private List<Program> resolvePrograms(String freeText, SearchQueryParser.ParsedQuery parsed) {
        String hint = parsed.programHint();
        if ((hint == null || hint.isBlank()) && (freeText == null || freeText.isBlank())) {
            return List.of();
        }
        String normalizedHint = SearchText.compact(applyAliases(hint));
        if (normalizedHint.isEmpty()) {
            return List.of();
        }

        record Scored(Program program, int score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Program program : programRepository.findByActiveTrueOrderByNameAsc()) {
            String name = SearchText.compact(program.getName());
            String code = SearchText.compact(program.getCode());
            int score = 0;
            if (!code.isEmpty() && code.equals(normalizedHint)) {
                score = 100;
            } else if (!name.isEmpty() && name.equals(normalizedHint)) {
                score = 95;
            } else if (!code.isEmpty() && normalizedHint.contains(code)) {
                score = 80;
            } else if (!name.isEmpty() && normalizedHint.contains(name)) {
                score = 70;
            } else if ((!name.isEmpty() && name.contains(normalizedHint))
                    || (!code.isEmpty() && code.contains(normalizedHint))) {
                score = 50;
            }
            if (score > 0) {
                scored.add(new Scored(program, score));
            }
        }
        scored.sort((a, b) -> {
            int byScore = Integer.compare(b.score(), a.score());
            return byScore != 0 ? byScore : a.program().getName().compareToIgnoreCase(b.program().getName());
        });
        return scored.stream().map(Scored::program).toList();
    }

    /** Applies the configured alias table so declared spellings collapse onto the real code. */
    private String applyAliases(String text) {
        String compact = SearchText.compact(text);
        if (compact.isEmpty()) {
            return "";
        }
        for (Map.Entry<String, String> alias : searchProperties.getProgramAliases().entrySet()) {
            if (SearchText.compact(alias.getKey()).equals(compact)) {
                return alias.getValue();
            }
        }
        return text;
    }
}