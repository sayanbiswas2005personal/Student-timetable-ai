package com.college.timetable.service.timetable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.college.timetable.dto.timetable.CreateTimetableRequest;
import com.college.timetable.dto.timetable.PublishTimetableRequest;
import com.college.timetable.dto.timetable.TimetableDto;
import com.college.timetable.dto.timetable.TimetableEntryDto;
import com.college.timetable.dto.timetable.TimetableEntryRequest;
import com.college.timetable.dto.timetable.TimetableValidationReport;
import com.college.timetable.entity.EntryType;
import com.college.timetable.entity.Faculty;
import com.college.timetable.entity.Room;
import com.college.timetable.entity.Section;
import com.college.timetable.entity.Subject;
import com.college.timetable.entity.Timetable;
import com.college.timetable.entity.TimetableEntry;
import com.college.timetable.entity.TimetableStatus;
import com.college.timetable.entity.VerificationStatus;
import com.college.timetable.exception.ApiException;
import com.college.timetable.repository.FacultyRepository;
import com.college.timetable.repository.RoomRepository;
import com.college.timetable.repository.SectionRepository;
import com.college.timetable.repository.SubjectRepository;
import com.college.timetable.repository.TimetableEntryRepository;
import com.college.timetable.repository.TimetableRepository;
import com.college.timetable.security.CurrentUser;
import com.college.timetable.service.audit.AuditService;

/**
 * Versioned timetable management: create drafts, edit periods, validate, publish and roll back.
 *
 * <p>Publishing is a single transaction that marks the new version PUBLISHED and closes the
 * previous one, so the lookup engine can never observe a half updated set of effective timetables.
 */
@Service
public class TimetableService {

    private final TimetableRepository timetableRepository;
    private final TimetableEntryRepository entryRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final FacultyRepository facultyRepository;
    private final RoomRepository roomRepository;
    private final AuditService auditService;

    public TimetableService(TimetableRepository timetableRepository,
                            TimetableEntryRepository entryRepository,
                            SectionRepository sectionRepository,
                            SubjectRepository subjectRepository,
                            FacultyRepository facultyRepository,
                            RoomRepository roomRepository,
                            AuditService auditService) {
        this.timetableRepository = timetableRepository;
        this.entryRepository = entryRepository;
        this.sectionRepository = sectionRepository;
        this.subjectRepository = subjectRepository;
        this.facultyRepository = facultyRepository;
        this.roomRepository = roomRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TimetableDto> list(Long sectionId) {
        var timetables = sectionId == null
                ? timetableRepository.findAll()
                : timetableRepository.findAll().stream()
                        .filter(t -> t.getSection().getId().equals(sectionId))
                        .toList();
        return timetables.stream()
                .sorted((a, b) -> {
                    int bySection = a.getSection().getProgram().getName()
                            .compareToIgnoreCase(b.getSection().getProgram().getName());
                    return bySection != 0 ? bySection : Integer.compare(b.getVersion(), a.getVersion());
                })
                .map(t -> TimetableDto.from(t, List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TimetableDto get(Long id) {
        Timetable timetable = require(id);
        List<TimetableEntryDto> entries = entryRepository.findAllWithRelations(timetable.getId())
                .stream().map(TimetableEntryDto::from).toList();
        return TimetableDto.from(timetable, entries);
    }

    @Transactional
    public TimetableDto createDraft(CreateTimetableRequest request) {
        Section section = sectionRepository.findById(request.sectionId())
                .orElseThrow(() -> ApiException.badRequest("SECTION_NOT_FOUND",
                        "Section " + request.sectionId() + " does not exist."));
        validateEffectiveDates(request.effectiveFrom(), request.effectiveTo());

        Integer nextVersion = timetableRepository.findMaxVersion(section.getId());
        Timetable timetable = new Timetable();
        timetable.setSection(section);
        timetable.setAcademicTerm(section.getAcademicTerm());
        timetable.setEffectiveFrom(request.effectiveFrom());
        timetable.setEffectiveTo(request.effectiveTo());
        timetable.setStatus(TimetableStatus.DRAFT);
        timetable.setVersion(nextVersion == null ? 1 : nextVersion + 1);
        timetable.setSourceFilename(request.sourceFilename());
        timetable.setCreatedBy(CurrentUser.id());

        Timetable saved = timetableRepository.save(timetable);
        audit("TIMETABLE_CREATE", saved.getId(),
                "Draft version %d for section %d".formatted(saved.getVersion(), section.getId()));
        return TimetableDto.from(saved, List.of());
    }

    @Transactional
    public TimetableDto update(Long id, CreateTimetableRequest request) {
        Timetable timetable = requireEditable(id);
        validateEffectiveDates(request.effectiveFrom(), request.effectiveTo());
        timetable.setEffectiveFrom(request.effectiveFrom());
        timetable.setEffectiveTo(request.effectiveTo());
        if (request.sourceFilename() != null) {
            timetable.setSourceFilename(request.sourceFilename());
        }
        Timetable saved = timetableRepository.save(timetable);
        audit("TIMETABLE_UPDATE", saved.getId(), "Effective dates updated");
        return get(saved.getId());
    }

    @Transactional
    public TimetableEntryDto upsertEntry(Long timetableId, Long entryId, TimetableEntryRequest request) {
        Timetable timetable = requireEditable(timetableId);

        TimetableEntry entry = entryId == null
                ? new TimetableEntry()
                : entryRepository.findById(entryId)
                        .orElseThrow(() -> ApiException.notFound("Entry " + entryId + " was not found."));
        if (entry.getId() != null && !entry.getTimetable().getId().equals(timetableId)) {
            throw ApiException.badRequest("ENTRY_MOVED",
                    "That entry belongs to a different timetable version.");
        }
        if (entryId == null) {
            entry.setTimetable(timetable);
            entry.setSection(timetable.getSection());
            entry.setSourcePageNumber(null);
        }

        if (!request.endTime().isAfter(request.startTime())) {
            throw ApiException.badRequest("INVALID_TIME_RANGE",
                    "The end time must be later than the start time.");
        }

        entry.setDayOfWeek(request.dayOfWeek());
        entry.setStartTime(request.startTime());
        entry.setEndTime(request.endTime());
        entry.setEntryType(parseEntryType(request.entryType()));
        entry.setSubject(resolveSubject(request.subjectId()));
        entry.setFaculty(resolveFaculty(request.facultyId()));
        entry.setRoom(resolveRoom(request.roomId()));
        entry.setRawSourceText(request.rawSourceText());
        entry.setVerificationStatus(VerificationStatus.VERIFIED);

        TimetableEntry saved = entryRepository.save(entry);
        assertNoOverlap(timetableId, saved);

        audit("TIMETABLE_ENTRY_UPSERT", timetableId,
                "Day %d %s-%s".formatted(saved.getDayOfWeek(), saved.getStartTime(), saved.getEndTime()));
        return TimetableEntryDto.from(saved);
    }

    @Transactional
    public void deleteEntry(Long timetableId, Long entryId) {
        TimetableEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> ApiException.notFound("Entry " + entryId + " was not found."));
        if (!entry.getTimetable().getId().equals(timetableId)) {
            throw ApiException.badRequest("ENTRY_MOVED", "That entry belongs to a different timetable version.");
        }
        entryRepository.delete(entry);
        audit("TIMETABLE_ENTRY_DELETE", timetableId, "Removed entry " + entryId);
    }

    @Transactional(readOnly = true)
    public TimetableValidationReport validate(Long timetableId) {
        Timetable timetable = require(timetableId);
        List<TimetableEntry> entries = entryRepository.findByTimetableIdOrderByDayOfWeekAscStartTimeAsc(timetableId);
        List<TimetableValidationReport.ValidationIssue> issues = new ArrayList<>();

        if (entries.isEmpty()) {
            issues.add(new TimetableValidationReport.ValidationIssue("ERROR",
                    "The timetable contains no periods.", null));
        }
        for (TimetableEntry entry : entries) {
            if (!entry.getEndTime().isAfter(entry.getStartTime())) {
                issues.add(new TimetableValidationReport.ValidationIssue("ERROR",
                        "Period ends at or before it starts.", entry.getId()));
            }
            if (entry.getEntryType() == EntryType.CLASS && entry.getSubject() == null) {
                issues.add(new TimetableValidationReport.ValidationIssue("ERROR",
                        "A class period has no subject assigned.", entry.getId()));
            }
            if (entry.getVerificationStatus() == VerificationStatus.UNVERIFIED) {
                issues.add(new TimetableValidationReport.ValidationIssue("WARNING",
                        "Period has not been verified by an administrator.", entry.getId()));
            }
        }
        for (int day = 1; day <= 7; day++) {
            for (TimetableEntry entry : entries) {
                if (entry.getDayOfWeek() != day) {
                    continue;
                }
                var overlapping = entryRepository.findOverlapping(timetableId, day,
                                entry.getStartTime(), entry.getEndTime()).stream()
                        .filter(other -> !other.getId().equals(entry.getId()))
                        .toList();
                for (TimetableEntry other : overlapping) {
                    if (other.getId() > entry.getId()) {
                        continue;
                    }
                    issues.add(new TimetableValidationReport.ValidationIssue("ERROR",
                            "Two periods overlap on %s between %s and %s."
                                    .formatted(java.time.DayOfWeek.of(day), entry.getStartTime(), entry.getEndTime()),
                            entry.getId()));
                }
            }
        }
        if (timetable.getEffectiveTo() != null
                && timetable.getEffectiveTo().isBefore(timetable.getEffectiveFrom())) {
            issues.add(new TimetableValidationReport.ValidationIssue("ERROR",
                    "The effective end date is before the effective start date.", null));
        }

        boolean publishable = issues.stream().noneMatch(i -> i.severity().equals("ERROR"));
        return new TimetableValidationReport(publishable, issues);
    }

    /**
     * Publishes a version. Runs in one transaction: the previous published version is closed and
     * marked SUPERSEDED, and the new version becomes PUBLISHED with its publish timestamp.
     */
    @Transactional
    public TimetableDto publish(Long timetableId, PublishTimetableRequest request) {
        Timetable timetable = require(timetableId);
        if (timetable.getStatus() == TimetableStatus.PUBLISHED) {
            throw ApiException.conflict("This version is already published.");
        }

        if (request != null && request.effectiveFrom() != null) {
            validateEffectiveDates(request.effectiveFrom(),
                    request.effectiveTo() == null ? timetable.getEffectiveTo() : request.effectiveTo());
            timetable.setEffectiveFrom(request.effectiveFrom());
            timetable.setEffectiveTo(request.effectiveTo());
        }
        validateEffectiveDates(timetable.getEffectiveFrom(), timetable.getEffectiveTo());

        TimetableValidationReport report = validate(timetableId);
        if (!report.publishable()) {
            List<String> errors = report.issues().stream()
                    .filter(issue -> issue.severity().equals("ERROR"))
                    .map(TimetableValidationReport.ValidationIssue::message)
                    .toList();
            throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "TIMETABLE_NOT_PUBLISHABLE",
                    "This version cannot be published until the problems below are fixed.", errors);
        }

        var previous = timetableRepository.findBySectionAndStatus(
                timetable.getSection().getId(), TimetableStatus.PUBLISHED);

        Instant now = Instant.now();
        for (Timetable old : previous) {
            if (old.getId().equals(timetableId)) {
                continue;
            }
            // Close the old version the day before the new one starts, keeping history intact.
            old.setEffectiveTo(timetable.getEffectiveFrom().minusDays(1));
            if (old.getEffectiveTo().isBefore(old.getEffectiveFrom())) {
                old.setEffectiveTo(old.getEffectiveFrom());
                old.setStatus(TimetableStatus.SUPERSEDED);
            }
            timetableRepository.save(old);
        }

        timetable.setStatus(TimetableStatus.PUBLISHED);
        timetable.setPublishedAt(now);
        timetable.setPublishedBy(CurrentUser.id());
        timetableRepository.save(timetable);

        audit("TIMETABLE_PUBLISH", timetableId,
                "Published version %d effective from %s".formatted(timetable.getVersion(),
                        timetable.getEffectiveFrom()));
        return get(timetableId);
    }

    /**
     * Rolls a section back to an earlier published version by closing the current one and
     * re-publishing the chosen one. History is kept, so the change can itself be rolled forward.
     */
    @Transactional
    public TimetableDto rollback(Long timetableId, PublishTimetableRequest request) {
        Timetable target = require(timetableId);
        if (target.getStatus() != TimetableStatus.PUBLISHED
                && target.getStatus() != TimetableStatus.SUPERSEDED) {
            throw ApiException.badRequest("NOT_ROLLBACKABLE",
                    "Only a published or superseded version can be rolled back to.");
        }
        TimetableValidationReport report = validate(timetableId);
        if (!report.publishable()) {
            throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "TIMETABLE_NOT_PUBLISHABLE",
                    "That version has unresolved problems and cannot be restored.", report.issues().stream()
                            .filter(i -> i.severity().equals("ERROR"))
                            .map(TimetableValidationReport.ValidationIssue::message).toList());
        }
        return publish(timetableId, request);
    }

    // ------------------------------------------------------------- helpers

    private void assertNoOverlap(Long timetableId, TimetableEntry candidate) {
        List<TimetableEntry> overlapping = entryRepository.findOverlapping(timetableId,
                        candidate.getDayOfWeek(), candidate.getStartTime(), candidate.getEndTime()).stream()
                .filter(other -> !other.getId().equals(candidate.getId()))
                .toList();
        if (!overlapping.isEmpty()) {
            Set<String> conflicts = new LinkedHashSet<>();
            conflicts.add(overlapping.get(0).getEntryType().name());
            conflicts.add(candidate.getEntryType().name());
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "TIMETABLE_OVERLAP",
                    "This period overlaps an existing period on "
                            + java.time.DayOfWeek.of(candidate.getDayOfWeek()) + ".",
                    "Overlapping entry types: " + String.join(", ", conflicts));
        }
    }

    private Timetable require(Long id) {
        return timetableRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Timetable " + id + " was not found."));
    }

    private Timetable requireEditable(Long id) {
        Timetable timetable = require(id);
        if (timetable.getStatus() == TimetableStatus.PUBLISHED) {
            throw ApiException.conflict("A published version is read only. Create a new draft version instead.");
        }
        return timetable;
    }

    private static void validateEffectiveDates(LocalDate from, LocalDate to) {
        if (from == null) {
            throw ApiException.badRequest("INVALID_EFFECTIVE_DATES", "An effective start date is required.");
        }
        if (to != null && to.isBefore(from)) {
            throw ApiException.badRequest("INVALID_EFFECTIVE_DATES",
                    "The effective end date must not be earlier than the start date.");
        }
    }

    private static EntryType parseEntryType(String value) {
        if (value == null || value.isBlank()) {
            return EntryType.CLASS;
        }
        try {
            return EntryType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_ENTRY_TYPE",
                    "entryType must be one of CLASS, BREAK or OTHER.");
        }
    }

    private Subject resolveSubject(Long id) {
        if (id == null) {
            return null;
        }
        return subjectRepository.findById(id)
                .orElseThrow(() -> ApiException.badRequest("SUBJECT_NOT_FOUND", "Subject " + id + " does not exist."));
    }

    private Faculty resolveFaculty(Long id) {
        if (id == null) {
            return null;
        }
        return facultyRepository.findById(id)
                .orElseThrow(() -> ApiException.badRequest("FACULTY_NOT_FOUND", "Faculty " + id + " does not exist."));
    }

    private Room resolveRoom(Long id) {
        if (id == null) {
            return null;
        }
        return roomRepository.findById(id)
                .orElseThrow(() -> ApiException.badRequest("ROOM_NOT_FOUND", "Room " + id + " does not exist."));
    }

    private void audit(String action, Long entityId, String detail) {
        auditService.recordAs(CurrentUser.id(), CurrentUser.username(), action, "Timetable", entityId, detail);
    }
}