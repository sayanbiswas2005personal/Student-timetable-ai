package com.college.timetable.service.lookup;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.college.timetable.dto.lookup.ClassInfo;
import com.college.timetable.dto.lookup.LookupResponse;
import com.college.timetable.dto.lookup.LookupStatus;
import com.college.timetable.dto.lookup.TimetableInfo;
import com.college.timetable.entity.EntryType;
import com.college.timetable.entity.Timetable;
import com.college.timetable.entity.TimetableEntry;
import com.college.timetable.repository.TimetableEntryRepository;
import com.college.timetable.util.TimeText;

/**
 * Decides which period a section is scheduled for at a given instant.
 *
 * <p>The engine only ever reads approved {@code PUBLISHED} rows from the database. It never
 * invents entries and never calls a language model.
 *
 * <p>Time comparisons use half open intervals: {@code start <= instant < end}. A period from
 * 10:00 to 11:00 is in progress at 10:30 and finished at 11:00.
 */
@Service
public class TimetableLookupEngine {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final TimetableEntryRepository entryRepository;
    private final TimetableSelectionService selectionService;
    private final Clock clock;
    private final ZoneId zone;

    public TimetableLookupEngine(TimetableEntryRepository entryRepository,
                                 TimetableSelectionService selectionService,
                                 Clock clock,
                                 ZoneId zone) {
        this.entryRepository = entryRepository;
        this.selectionService = selectionService;
        this.clock = clock;
        this.zone = zone;
    }

    /** Internal, non HTTP result used by both the student and the section lookup paths. */
    public record EngineResult(LookupStatus status,
                               ClassInfo currentClass,
                               ClassInfo nextClass,
                               boolean nextClassOnLaterDay,
                               TimetableInfo timetable,
                               List<String> notices,
                               List<ClassInfo> conflictingClasses) {
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(clock).withZoneSameInstant(zone);
    }

    /**
     * Resolves the schedule for one section.
     *
     * @param sectionId    authoritative section taken from the database
     * @param at           instant to evaluate, already expressed in the college timezone
     */
    @Transactional(readOnly = true)
    public EngineResult resolve(Long sectionId, ZonedDateTime at) {
        List<String> notices = new ArrayList<>();
        LocalDate date = at.toLocalDate();
        LocalTime time = at.toLocalTime();
        int dayOfWeek = at.getDayOfWeek().getValue();

        TimetableSelectionService.Selection selection = selectionService.selectForDate(sectionId, date);
        notices.addAll(selection.notices());

        if (!selection.found()) {
            LookupStatus status = selection.reason() == TimetableSelectionService.MissingReason.NOT_PUBLISHED
                    ? LookupStatus.TIMETABLE_NOT_PUBLISHED
                    : LookupStatus.NO_TIMETABLE;
            return new EngineResult(status, null, null, false, null, notices, List.of());
        }

        Timetable timetable = selection.timetable().orElseThrow();
        TimetableInfo info = TimetableSelectionService.toInfo(timetable);

        List<TimetableEntry> dayEntries =
                entryRepository.findDayWithRelations(timetable.getId(), dayOfWeek);

        List<TimetableEntry> occupying = dayEntries.stream()
                .filter(entry -> entry.contains(time))
                .toList();

        if (occupying.size() > 1) {
            List<ClassInfo> conflicting = occupying.stream().map(this::toClassInfo).toList();
            notices.add("Data quality issue: " + occupying.size()
                    + " entries in timetable version " + timetable.getVersion()
                    + " claim the same time on " + at.getDayOfWeek() + ".");
            NextClass conflictingNext = findNextClass(timetable, at);
            return new EngineResult(LookupStatus.TIMETABLE_CONFLICT, null,
                    conflictingNext == null ? null : toClassInfo(conflictingNext.entry()),
                    conflictingNext != null && conflictingNext.daysAhead() > 0,
                    info, notices, conflicting);
        }

        NextClass next = findNextClass(timetable, at);
        ClassInfo nextInfo = next == null ? null : toClassInfo(next.entry());
        boolean nextLaterDay = next != null && next.daysAhead() > 0;

        if (occupying.isEmpty()) {
            return new EngineResult(LookupStatus.NO_CLASS_NOW, null, nextInfo, nextLaterDay, info, notices, List.of());
        }

        TimetableEntry entry = occupying.get(0);
        LookupStatus status = switch (entry.getEntryType()) {
            case CLASS -> LookupStatus.CLASS_IN_PROGRESS;
            case BREAK -> LookupStatus.BREAK;
            case OTHER -> LookupStatus.OTHER_IN_PROGRESS;
        };
        return new EngineResult(status, toClassInfo(entry), nextInfo, nextLaterDay, info, notices, List.of());
    }

    /** Builds the full HTTP payload, filling in the college local calendar fields. */
    public LookupResponse toResponse(EngineResult result, StudentSummaryProvider student, ZonedDateTime at) {
        var studentSummary = student == null ? null : student.summary();
        String message = describe(result.status(), result.currentClass(), result.nextClass(), at);
        return new LookupResponse(
                result.status(),
                message,
                at.toInstant(),
                zone.getId(),
                at.toLocalDate().toString(),
                at.toLocalTime().format(HHMM),
                at.getDayOfWeek().name(),
                studentSummary,
                result.currentClass(),
                result.nextClass(),
                result.nextClassOnLaterDay(),
                result.timetable(),
                result.notices(),
                List.of(),
                result.conflictingClasses());
    }

    /** Supplies the verified student identity for the response, or nothing for a section lookup. */
    @FunctionalInterface
    public interface StudentSummaryProvider {
        com.college.timetable.dto.lookup.StudentSummary summary();
    }

    private String describe(LookupStatus status, ClassInfo current, ClassInfo next, ZonedDateTime at) {
        return switch (status) {
            case CLASS_IN_PROGRESS -> "Scheduled: %s from %s to %s."
                    .formatted(nullSafe(current.subjectName(), "Unspecified subject"),
                            nullSafe(current.startTime(), "?"), nullSafe(current.endTime(), "?"));
            case BREAK -> "Declared break from %s to %s in the published timetable."
                    .formatted(nullSafe(current.startTime(), "?"), nullSafe(current.endTime(), "?"));
            case OTHER_IN_PROGRESS -> "The published timetable records %s from %s to %s for this slot."
                    .formatted(nullSafe(current.subjectName(), "another activity"),
                            nullSafe(current.startTime(), "?"), nullSafe(current.endTime(), "?"));
            case NO_CLASS_NOW -> "No class is scheduled for this section at " + timeOf(at) + "."
                    + nextSentence(next);
            case NO_TIMETABLE -> "No timetable has been entered for this section yet.";
            case TIMETABLE_NOT_PUBLISHED ->
                    "A timetable exists for this section but no published version is in force today.";
            case STUDENT_NOT_FOUND -> "No student record matches that registration number.";
            case STUDENT_INACTIVE -> "This student record is marked inactive in the college system.";
            case AMBIGUOUS_SEARCH -> "That search matched more than one section. Please choose one.";
            case TIMETABLE_CONFLICT ->
                    "The published timetable contains overlapping entries for this time slot, "
                            + "so the expected class cannot be determined reliably. Please have an administrator review it.";
        };
    }

    private static String nextSentence(ClassInfo next) {
        if (next == null) {
            return "";
        }
        return " The next scheduled class is " + nullSafe(next.subjectName(), "Unspecified subject")
                + " at " + next.startTime() + " on " + next.dayName() + ".";
    }

    private static String timeOf(ZonedDateTime at) {
        return at.toLocalTime().format(HHMM);
    }

    private static String nullSafe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Next scheduled CLASS, together with how far ahead it is. */
    private record NextClass(TimetableEntry entry, int daysAhead) {
    }

    /**
     * Finds the next scheduled CLASS after {@code at}.
     *
     * <p>Candidate days are projected forward: same day for entries later today, tomorrow for
     * later weekdays, and next week for weekdays that have already passed this week.
     */
    private NextClass findNextClass(Timetable timetable, ZonedDateTime at) {
        LocalDate date = at.toLocalDate();
        LocalTime time = at.toLocalTime();
        int dayOfWeek = at.getDayOfWeek().getValue();

        List<TimetableEntry> week = entryRepository.findAllWithRelations(timetable.getId());
        LocalDateTime best = null;
        TimetableEntry bestEntry = null;
        int bestDaysAhead = 0;

        for (TimetableEntry entry : week) {
            if (entry.getEntryType() != EntryType.CLASS || entry.getStartTime() == null) {
                continue;
            }
            int daysAhead = forwardDays(dayOfWeek, entry.getDayOfWeek());
            LocalDateTime candidate = date.plusDays(daysAhead).atTime(entry.getStartTime());
            if (!candidate.isAfter(LocalDateTime.of(date, time))) {
                continue;
            }
            if (best == null || candidate.isBefore(best)) {
                best = candidate;
                bestEntry = entry;
                bestDaysAhead = daysAhead;
            }
        }
        return bestEntry == null ? null : new NextClass(bestEntry, bestDaysAhead);
    }

    /** 0 for today, 1..6 for the following days. */
    private static int forwardDays(int currentDayOfWeek, int targetDayOfWeek) {
        int delta = targetDayOfWeek - currentDayOfWeek;
        return delta >= 0 ? delta : delta + 7;
    }

    public ClassInfo toClassInfo(TimetableEntry entry) {
        var subject = entry.getSubject();
        var faculty = entry.getFaculty();
        var room = entry.getRoom();
        return new ClassInfo(
                subject == null ? null : subject.getSubjectCode(),
                subject == null ? null : subject.getSubjectName(),
                faculty == null ? null : faculty.getFacultyName(),
                room == null ? null : room.getRoomCode(),
                room == null ? null : room.getBuilding(),
                entry.getDayOfWeek(),
                dayName(entry.getDayOfWeek()),
                TimeText.format(entry.getStartTime()),
                TimeText.format(entry.getEndTime()),
                entry.getEntryType().name(),
                entry.getTimetable() == null ? null : entry.getTimetable().getId(),
                entry.getTimetable() == null ? 0 : entry.getTimetable().getVersion());
    }

    public static String dayName(int dayOfWeek) {
        return java.time.DayOfWeek.of(dayOfWeek).name();
    }
}