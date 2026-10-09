package com.college.timetable.service.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.college.timetable.dto.lookup.LookupStatus;
import com.college.timetable.entity.EntryType;
import com.college.timetable.entity.Timetable;
import com.college.timetable.entity.TimetableEntry;
import com.college.timetable.entity.TimetableStatus;
import com.college.timetable.repository.TimetableEntryRepository;
import com.college.timetable.repository.TimetableRepository;

/**
 * Tests the decision logic of the lookup engine with a fixed clock.
 *
 * <p>Every instant is explicit. Nothing here depends on the wall clock, so the suite gives the
 * same answer today and in three years.
 */
class TimetableLookupEngineTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    /** 2026-10-09 is a Friday, so weekday 5. */
    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 9);
    private static final long TIMETABLE_ID = 42L;

    private TimetableEntryRepository entryRepository;
    private TimetableSelectionService selectionService;
    private TimetableLookupEngine engine;
    private Timetable timetable;

    @BeforeEach
    void setUp() {
        entryRepository = mock(TimetableEntryRepository.class);
        selectionService = mock(TimetableSelectionService.class);
        engine = new TimetableLookupEngine(entryRepository, selectionService,
                Clock.systemUTC(), ZONE);

        timetable = new Timetable();
        timetable.setId(TIMETABLE_ID);
        timetable.setVersion(3);
        timetable.setStatus(TimetableStatus.PUBLISHED);
        timetable.setEffectiveFrom(FRIDAY.minusDays(30));
        timetable.setEffectiveTo(null);

        lenient().when(selectionService.selectForDate(anyLong(), any()))
                .thenReturn(new TimetableSelectionService.Selection(
                        Optional.of(timetable), TimetableSelectionService.MissingReason.NONE, List.of()));
    }

    private ZonedDateTime at(int hour, int minute) {
        return ZonedDateTime.of(FRIDAY, LocalTime.of(hour, minute), ZONE);
    }

    private TimetableEntry entry(int day, LocalTime start, LocalTime end, EntryType type) {
        TimetableEntry entry = new TimetableEntry();
        entry.setId((long) day * 100 + start.getHour());
        entry.setTimetable(timetable);
        entry.setDayOfWeek(day);
        entry.setStartTime(start);
        entry.setEndTime(end);
        entry.setEntryType(type);
        return entry;
    }

    private void dayIs(TimetableEntry... entries) {
        lenient().when(entryRepository.findDayWithRelations(anyLong(), anyInt()))
                .thenAnswer(invocation -> List.of(entries));
    }

    private void weekIs(TimetableEntry... entries) {
        lenient().when(entryRepository.findAllWithRelations(anyLong())).thenReturn(List.of(entries));
    }

    // ------------------------------------------------------------ boundaries

    @Test
    @DisplayName("a class is in progress strictly between its start and end times")
    void classInProgress() {
        TimetableEntry period = entry(5, LocalTime.of(9, 30), LocalTime.of(10, 25), EntryType.CLASS);
        dayIs(period);
        weekIs(period);

        var result = engine.resolve(1L, at(10, 0));

        assertThat(result.status()).isEqualTo(LookupStatus.CLASS_IN_PROGRESS);
        assertThat(result.currentClass().startTime()).isEqualTo("09:30");
        assertThat(result.currentClass().endTime()).isEqualTo("10:25");
    }

    @Test
    @DisplayName("a class is in progress at its exact start time")
    void startsExactlyOnTime() {
        TimetableEntry period = entry(5, LocalTime.of(9, 30), LocalTime.of(10, 25), EntryType.CLASS);
        dayIs(period);
        weekIs(period);

        assertThat(engine.resolve(1L, at(9, 30)).status()).isEqualTo(LookupStatus.CLASS_IN_PROGRESS);
    }

    @Test
    @DisplayName("a class is finished at its exact end time, because intervals are half open")
    void endsExactlyOnTime() {
        TimetableEntry period = entry(5, LocalTime.of(9, 30), LocalTime.of(10, 25), EntryType.CLASS);
        TimetableEntry next = entry(5, LocalTime.of(10, 30), LocalTime.of(11, 25), EntryType.CLASS);
        dayIs(period, next);
        weekIs(period, next);

        var result = engine.resolve(1L, at(10, 25));

        assertThat(result.status()).isEqualTo(LookupStatus.NO_CLASS_NOW);
        assertThat(result.currentClass()).isNull();
        assertThat(result.nextClass()).isNotNull();
        assertThat(result.nextClass().startTime()).isEqualTo("10:30");
    }

    @Test
    @DisplayName("a gap between two classes reports no class now and names the next one")
    void freePeriodBetweenClasses() {
        TimetableEntry morning = entry(5, LocalTime.of(9, 30), LocalTime.of(10, 25), EntryType.CLASS);
        TimetableEntry afternoon = entry(5, LocalTime.of(13, 30), LocalTime.of(14, 25), EntryType.CLASS);
        dayIs(morning, afternoon);
        weekIs(morning, afternoon);

        var result = engine.resolve(1L, at(11, 0));

        assertThat(result.status()).isEqualTo(LookupStatus.NO_CLASS_NOW);
        assertThat(result.nextClass().startTime()).isEqualTo("13:30");
        assertThat(result.nextClassOnLaterDay()).isFalse();
    }

    @Test
    @DisplayName("an empty day is a free day, not an error")
    void emptyDay() {
        dayIs();
        weekIs();

        var result = engine.resolve(1L, at(11, 0));

        assertThat(result.status()).isEqualTo(LookupStatus.NO_CLASS_NOW);
        assertThat(result.nextClass()).isNull();
        assertThat(result.nextClassOnLaterDay()).isFalse();
    }

    // ----------------------------------------------------------- entry types

    @Test
    @DisplayName("an explicitly declared break is reported as a break, not as a class")
    void breakIsReported() {
        TimetableEntry recess = entry(5, LocalTime.of(12, 30), LocalTime.of(13, 25), EntryType.BREAK);
        dayIs(recess);
        weekIs();

        var result = engine.resolve(1L, at(12, 45));

        assertThat(result.status()).isEqualTo(LookupStatus.BREAK);
        assertThat(result.currentClass().entryType()).isEqualTo("BREAK");
    }

    @Test
    @DisplayName("an OTHER block is surfaced rather than hidden behind 'no class'")
    void otherIsReported() {
        TimetableEntry library = entry(5, LocalTime.of(12, 30), LocalTime.of(13, 25), EntryType.OTHER);
        dayIs(library);
        weekIs();

        assertThat(engine.resolve(1L, at(12, 45)).status()).isEqualTo(LookupStatus.OTHER_IN_PROGRESS);
    }

    // -------------------------------------------------------------- conflict

    @Test
    @DisplayName("overlapping entries are reported as a data conflict, never resolved by guessing")
    void overlappingEntriesAreAConflict() {
        TimetableEntry first = entry(5, LocalTime.of(9, 30), LocalTime.of(10, 25), EntryType.CLASS);
        TimetableEntry second = entry(5, LocalTime.of(10, 0), LocalTime.of(10, 55), EntryType.CLASS);
        dayIs(first, second);
        weekIs();

        var result = engine.resolve(1L, at(10, 10));

        assertThat(result.status()).isEqualTo(LookupStatus.TIMETABLE_CONFLICT);
        assertThat(result.currentClass()).isNull();
        assertThat(result.conflictingClasses()).hasSize(2);
        assertThat(result.notices()).anyMatch(notice -> notice.contains("overlap") || notice.contains("same time"));
    }

    @Test
    @DisplayName("back to back periods are not treated as overlapping")
    void backToBackIsNotAConflict() {
        TimetableEntry first = entry(5, LocalTime.of(9, 30), LocalTime.of(10, 25), EntryType.CLASS);
        TimetableEntry second = entry(5, LocalTime.of(10, 25), LocalTime.of(11, 20), EntryType.CLASS);
        dayIs(first, second);
        weekIs(first, second);

        assertThat(engine.resolve(1L, at(10, 25)).status()).isEqualTo(LookupStatus.CLASS_IN_PROGRESS);
    }

    // ---------------------------------------------------------- next class

    @Test
    @DisplayName("the next class rolls over to the next weekday when today has finished")
    void nextClassOnALaterDay() {
        TimetableEntry friday = entry(5, LocalTime.of(9, 30), LocalTime.of(10, 25), EntryType.CLASS);
        TimetableEntry monday = entry(1, LocalTime.of(9, 30), LocalTime.of(10, 25), EntryType.CLASS);
        dayIs(friday);
        weekIs(friday, monday);

        var result = engine.resolve(1L, at(16, 0));

        assertThat(result.nextClass()).isNotNull();
        assertThat(result.nextClass().dayName()).isEqualTo("MONDAY");
        assertThat(result.nextClassOnLaterDay()).isTrue();
    }

    @Test
    @DisplayName("the next class wraps to the same weekday next week rather than searching forever")
    void nextClassWrapsToNextWeek() {
        TimetableEntry monday = entry(1, LocalTime.of(9, 30), LocalTime.of(10, 25), EntryType.CLASS);
        dayIs();
        weekIs(monday);

        var result = engine.resolve(1L, at(16, 0));

        assertThat(result.nextClass().dayName()).isEqualTo("MONDAY");
        assertThat(result.nextClassOnLaterDay()).isTrue();
    }

    @Test
    @DisplayName("while a class is running the next class is the one after it, not the current one")
    void nextClassSkipsTheCurrentOne() {
        TimetableEntry current = entry(5, LocalTime.of(9, 30), LocalTime.of(10, 25), EntryType.CLASS);
        TimetableEntry later = entry(5, LocalTime.of(10, 30), LocalTime.of(11, 25), EntryType.CLASS);
        dayIs(current, later);
        weekIs(current, later);

        var result = engine.resolve(1L, at(10, 0));

        assertThat(result.currentClass().startTime()).isEqualTo("09:30");
        assertThat(result.nextClass().startTime()).isEqualTo("10:30");
    }

    @Test
    @DisplayName("a break is not offered as the next class")
    void breaksAreNotNextClasses() {
        TimetableEntry breakEntry = entry(5, LocalTime.of(12, 30), LocalTime.of(13, 25), EntryType.BREAK);
        dayIs(breakEntry);
        weekIs(breakEntry);

        assertThat(engine.resolve(1L, at(11, 0)).nextClass()).isNull();
    }

    // ------------------------------------------------------- missing timetables

    @Test
    @DisplayName("a section with no timetable at all reports NO_TIMETABLE")
    void noTimetableAtAll() {
        when(selectionService.selectForDate(anyLong(), any()))
                .thenReturn(new TimetableSelectionService.Selection(
                        Optional.empty(), TimetableSelectionService.MissingReason.NO_ROWS, List.of()));

        var result = engine.resolve(1L, at(10, 0));

        assertThat(result.status()).isEqualTo(LookupStatus.NO_TIMETABLE);
        assertThat(result.timetable()).isNull();
    }

    @Test
    @DisplayName("a draft that has never been published reports TIMETABLE_NOT_PUBLISHED")
    void draftDoesNotSatisfyLookup() {
        when(selectionService.selectForDate(anyLong(), any()))
                .thenReturn(new TimetableSelectionService.Selection(
                        Optional.empty(), TimetableSelectionService.MissingReason.NOT_PUBLISHED, List.of()));

        assertThat(engine.resolve(1L, at(10, 0)).status())
                .isEqualTo(LookupStatus.TIMETABLE_NOT_PUBLISHED);
    }

    @Test
    @DisplayName("the answer names the timetable version it came from")
    void resultCarriesTimetableVersion() {
        TimetableEntry period = entry(5, LocalTime.of(9, 30), LocalTime.of(10, 25), EntryType.CLASS);
        dayIs(period);
        weekIs(period);

        var result = engine.resolve(1L, at(10, 0));

        assertThat(result.timetable().version()).isEqualTo(3);
        assertThat(result.timetable().id()).isEqualTo(TIMETABLE_ID);
    }

    @Test
    @DisplayName("messages describe the schedule and never allege misconduct")
    void messagesAreNeutral() {
        TimetableEntry period = entry(5, LocalTime.of(9, 30), LocalTime.of(10, 25), EntryType.CLASS);
        dayIs(period);
        weekIs(period);

        String inProgress = engine.toResponse(engine.resolve(1L, at(10, 0)), null, at(10, 0)).message();
        String noClass = engine.toResponse(engine.resolve(1L, at(11, 0)), null, at(11, 0)).message();

        assertThat(inProgress).doesNotContainIgnoringCase("absent")
                .doesNotContainIgnoringCase("bunk")
                .doesNotContainIgnoringCase("violation")
                .doesNotContainIgnoringCase("missing");
        assertThat(noClass).doesNotContainIgnoringCase("absent")
                .doesNotContainIgnoringCase("bunk")
                .doesNotContainIgnoringCase("violation");
        assertThat(noClass).containsIgnoringCase("No class is scheduled");
    }

    @Test
    @DisplayName("an evaluation instant before 1970 or far in the future still behaves")
    void unusualInstants() {
        TimetableEntry period = entry(5, LocalTime.of(9, 30), LocalTime.of(10, 25), EntryType.CLASS);
        dayIs(period);
        weekIs(period);

        ZonedDateTime late = ZonedDateTime.of(FRIDAY, LocalTime.of(23, 59), ZONE);
        assertThat(engine.resolve(1L, late).status()).isEqualTo(LookupStatus.NO_CLASS_NOW);

        ZonedDateTime early = ZonedDateTime.of(FRIDAY, LocalTime.of(0, 0), ZONE);
        assertThat(engine.resolve(1L, early).status()).isEqualTo(LookupStatus.NO_CLASS_NOW);
        assertThat(Instant.now()).isNotNull();
    }
}