package com.college.timetable.service.lookup;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.college.timetable.dto.lookup.TimetableInfo;
import com.college.timetable.entity.Timetable;
import com.college.timetable.entity.TimetableStatus;
import com.college.timetable.repository.TimetableRepository;

/**
 * Chooses which timetable version is authoritative for a section on a given date.
 *
 * <p>Rules enforced here:
 * <ul>
 *   <li>only {@link TimetableStatus#PUBLISHED} versions are ever returned, so a newer draft can
 *       never shadow an older published timetable;</li>
 *   <li>the version must be in force on the date (effectiveFrom/effectiveTo, both inclusive);</li>
 *   <li>when several published versions are in force, the highest version wins and the overlap
 *       is reported back as a notice rather than being silently ignored.</li>
 * </ul>
 *
 * <p>When nothing can be selected the reason distinguishes the two cases a staff member needs to
 * hear differently: a draft exists for today but nobody approved it yet
 * ({@code TIMETABLE_NOT_PUBLISHED}), or there is simply no timetable covering this date
 * ({@code NO_TIMETABLE}).
 */
@Service
public class TimetableSelectionService {

    private final TimetableRepository timetableRepository;

    public TimetableSelectionService(TimetableRepository timetableRepository) {
        this.timetableRepository = timetableRepository;
    }

    /** Outcome of version selection, including the reason nothing could be selected. */
    public record Selection(Optional<Timetable> timetable,
                            MissingReason reason,
                            List<String> notices) {

        public boolean found() {
            return timetable.isPresent();
        }
    }

    public enum MissingReason {
        NONE,
        /** No timetable row of any status exists for this section. */
        NO_ROWS,
        /** A draft exists and is in force today, but nobody has approved it. */
        NOT_PUBLISHED
    }

    @Transactional(readOnly = true)
    public Selection selectForDate(Long sectionId, LocalDate date) {
        List<Timetable> effective = timetableRepository.findEffectivePublished(sectionId, date);
        List<String> notices = new ArrayList<>();

        if (!effective.isEmpty()) {
            if (effective.size() > 1) {
                notices.add("More than one published version is in force on " + date
                        + " (versions " + effective.stream().map(t -> Integer.toString(t.getVersion())).toList()
                        + "). The highest version was used.");
            }
            return new Selection(Optional.of(effective.get(0)), MissingReason.NONE, notices);
        }

        boolean unpublishedInForce = !timetableRepository.findEffectiveUnpublished(sectionId, date).isEmpty();
        return new Selection(Optional.empty(),
                unpublishedInForce ? MissingReason.NOT_PUBLISHED : MissingReason.NO_ROWS,
                notices);
    }

    public static TimetableInfo toInfo(Timetable timetable) {
        return new TimetableInfo(timetable.getId(), timetable.getVersion(),
                timetable.getStatus().name(), timetable.getEffectiveFrom(), timetable.getEffectiveTo(),
                timetable.getSourceFilename());
    }
}