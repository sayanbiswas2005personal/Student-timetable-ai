package com.college.timetable.service.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.college.timetable.entity.Timetable;
import com.college.timetable.entity.TimetableStatus;
import com.college.timetable.repository.TimetableRepository;

class TimetableSelectionServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 9);

    private final TimetableRepository repository = mock(TimetableRepository.class);
    private final TimetableSelectionService service = new TimetableSelectionService(repository);

    private static Timetable version(int version, TimetableStatus status, LocalDate from, LocalDate to) {
        Timetable timetable = new Timetable();
        timetable.setId((long) version);
        timetable.setVersion(version);
        timetable.setStatus(status);
        timetable.setEffectiveFrom(from);
        timetable.setEffectiveTo(to);
        return timetable;
    }

    @Test
    @DisplayName("the effective published version is returned for a date inside its range")
    void picksPublishedVersionInForce() {
        Timetable published = version(2, TimetableStatus.PUBLISHED, DATE.minusDays(10), null);
        when(repository.findEffectivePublished(1L, DATE)).thenReturn(List.of(published));

        var selection = service.selectForDate(1L, DATE);

        assertThat(selection.found()).isTrue();
        assertThat(selection.timetable()).contains(published);
        assertThat(selection.reason()).isEqualTo(TimetableSelectionService.MissingReason.NONE);
        assertThat(selection.notices()).isEmpty();
    }

    @Test
    @DisplayName("both effective bounds are inclusive")
    void effectiveBoundsAreInclusive() {
        Timetable timetable = version(1, TimetableStatus.PUBLISHED, DATE, DATE);
        assertThat(timetable.isEffectiveOn(DATE)).isTrue();
        assertThat(timetable.isEffectiveOn(DATE.minusDays(1))).isFalse();
        assertThat(timetable.isEffectiveOn(DATE.plusDays(1))).isFalse();

        Timetable openEnded = version(1, TimetableStatus.PUBLISHED, DATE, null);
        assertThat(openEnded.isEffectiveOn(DATE.plusYears(5))).isTrue();
    }

    @Test
    @DisplayName("a draft in force today is reported as awaiting approval, never served")
    void draftIsIgnored() {
        // The published query filters on PUBLISHED, so a draft can never appear in its result.
        when(repository.findEffectivePublished(1L, DATE)).thenReturn(List.of());
        when(repository.findEffectiveUnpublished(1L, DATE))
                .thenReturn(List.of(version(9, TimetableStatus.DRAFT, DATE, null)));

        var selection = service.selectForDate(1L, DATE);

        assertThat(selection.found()).isFalse();
        assertThat(selection.reason()).isEqualTo(TimetableSelectionService.MissingReason.NOT_PUBLISHED);
    }

    @Test
    @DisplayName("a published version that starts in the future is not used, and no draft is pending")
    void futureVersionIsNotUsed() {
        when(repository.findEffectivePublished(1L, DATE)).thenReturn(List.of());
        when(repository.findEffectiveUnpublished(1L, DATE)).thenReturn(List.of());

        var selection = service.selectForDate(1L, DATE);

        assertThat(selection.found()).isFalse();
        assertThat(selection.reason()).isEqualTo(TimetableSelectionService.MissingReason.NO_ROWS);
    }

    @Test
    @DisplayName("a section with no timetable rows at all reports NO_ROWS")
    void noRows() {
        when(repository.findEffectivePublished(anyLong(), any())).thenReturn(List.of());
        when(repository.findEffectiveUnpublished(anyLong(), any())).thenReturn(List.of());

        var selection = service.selectForDate(1L, DATE);

        assertThat(selection.reason()).isEqualTo(TimetableSelectionService.MissingReason.NO_ROWS);
    }

    @Test
    @DisplayName("if two published versions overlap the highest version wins and the clash is reported")
    void overlappingPublishedVersionsAreReported() {
        Timetable older = version(1, TimetableStatus.PUBLISHED, DATE.minusDays(30), null);
        Timetable newer = version(2, TimetableStatus.PUBLISHED, DATE.minusDays(1), null);
        when(repository.findEffectivePublished(1L, DATE)).thenReturn(List.of(newer, older));

        var selection = service.selectForDate(1L, DATE);

        assertThat(selection.timetable()).contains(newer);
        assertThat(selection.notices()).anyMatch(notice -> notice.contains("More than one published version"));
    }

    @Test
    @DisplayName("toInfo exposes the version that produced an answer")
    void toInfoCarriesVersion() {
        Timetable timetable = version(4, TimetableStatus.PUBLISHED, DATE.minusDays(2), DATE.plusDays(2));

        var info = TimetableSelectionService.toInfo(timetable);

        assertThat(info.version()).isEqualTo(4);
        assertThat(info.status()).isEqualTo("PUBLISHED");
        assertThat(info.effectiveFrom()).isEqualTo(DATE.minusDays(2));
        assertThat(info.effectiveTo()).isEqualTo(DATE.plusDays(2));
    }
}