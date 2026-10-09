package com.college.timetable.service.lookup;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.college.timetable.dto.lookup.ClassInfo;
import com.college.timetable.dto.lookup.LookupStatus;
import com.college.timetable.dto.lookup.SectionTimetableResponse;
import com.college.timetable.entity.EntryType;
import com.college.timetable.entity.Section;
import com.college.timetable.entity.TimetableEntry;
import com.college.timetable.exception.ApiException;
import com.college.timetable.repository.SectionRepository;
import com.college.timetable.repository.TimetableEntryRepository;
import com.college.timetable.util.TimeText;

/** Builds the whole week view of one section's effective published timetable. */
@Service
public class SectionTimetableService {

    private final SectionRepository sectionRepository;
    private final TimetableEntryRepository entryRepository;
    private final TimetableSelectionService selectionService;
    private final TimetableLookupEngine engine;

    public SectionTimetableService(SectionRepository sectionRepository,
                                   TimetableEntryRepository entryRepository,
                                   TimetableSelectionService selectionService,
                                   TimetableLookupEngine engine) {
        this.sectionRepository = sectionRepository;
        this.entryRepository = entryRepository;
        this.selectionService = selectionService;
        this.engine = engine;
    }

    @Transactional(readOnly = true)
    public SectionTimetableResponse week(Long sectionId, ZonedDateTime at) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> ApiException.notFound("Section " + sectionId + " was not found."));

        var program = section.getProgram();
        var term = section.getAcademicTerm();

        var selection = selectionService.selectForDate(sectionId, at.toLocalDate());
        if (!selection.found()) {
            LookupStatus status = selection.reason() == TimetableSelectionService.MissingReason.NOT_PUBLISHED
                    ? LookupStatus.TIMETABLE_NOT_PUBLISHED
                    : LookupStatus.NO_TIMETABLE;
            return new SectionTimetableResponse(sectionId, program.getName(), term.getAcademicYear(),
                    term.getSemesterNumber(), section.getSectionName(), null, at.getZone().getId(),
                    at.toLocalDate().toString(), at.getDayOfWeek().name(),
                    TimeText.format(at.toLocalTime()), List.of(), status,
                    "No published timetable is in force for this section.");
        }

var timetable = selection.timetable().orElseThrow();
var timetableId = timetable.getId();
var entries = entryRepository.findAllWithRelations(timetableId);

        List<SectionTimetableResponse.DaySchedule> days = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            final int dayOfWeek = day;
            List<ClassInfo> periods = entries.stream()
                    .filter(entry -> entry.getDayOfWeek() == dayOfWeek)
                    .sorted(java.util.Comparator.comparing(TimetableEntry::getStartTime))
                    .map(engine::toClassInfo)
                    .toList();
            days.add(new SectionTimetableResponse.DaySchedule(day,
                    TimetableLookupEngine.dayName(day), periods));
        }

        boolean hasAnyClass = entries.stream().anyMatch(entry -> entry.getEntryType() == EntryType.CLASS);

        return new SectionTimetableResponse(sectionId, program.getName(), term.getAcademicYear(),
                term.getSemesterNumber(), section.getSectionName(),
                TimetableSelectionService.toInfo(timetable), at.getZone().getId(),
                at.toLocalDate().toString(), at.getDayOfWeek().name(),
                TimeText.format(at.toLocalTime()), days,
                hasAnyClass ? LookupStatus.CLASS_IN_PROGRESS : LookupStatus.NO_CLASS_NOW,
                hasAnyClass ? "Published timetable in force for this date."
                        : "The published timetable for this date contains no scheduled classes.");
    }
}