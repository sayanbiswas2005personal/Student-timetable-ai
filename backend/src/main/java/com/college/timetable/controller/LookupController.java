package com.college.timetable.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.college.timetable.dto.lookup.AmbiguityOption;
import com.college.timetable.dto.lookup.ClassInfo;
import com.college.timetable.dto.lookup.LookupResponse;
import com.college.timetable.dto.lookup.LookupStatus;
import com.college.timetable.dto.lookup.SectionTimetableResponse;
import com.college.timetable.exception.ApiException;
import com.college.timetable.service.lookup.SectionSearchService;
import com.college.timetable.service.lookup.SectionTimetableService;
import com.college.timetable.service.lookup.StudentLookupService;
import com.college.timetable.service.lookup.TimetableLookupEngine;
import com.college.timetable.util.TimeText;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * The core lookup endpoints.
 *
 * <p>All of them default to "now" in the configured college timezone. The optional {@code at}
 * parameter exists for historical lookup and for testing with fixed instants; it is recorded in
 * the audit trail when it is used.
 */
@RestController
@RequestMapping("/api/lookup")
@Tag(name = "Timetable lookup")
public class LookupController {

    private final StudentLookupService studentLookupService;
    private final SectionSearchService sectionSearchService;
    private final SectionTimetableService sectionTimetableService;
    private final TimetableLookupEngine engine;
    private final ZoneId collegeZone;

    public LookupController(StudentLookupService studentLookupService,
                            SectionSearchService sectionSearchService,
                            SectionTimetableService sectionTimetableService,
                            TimetableLookupEngine engine,
                            ZoneId collegeZone) {
        this.studentLookupService = studentLookupService;
        this.sectionSearchService = sectionSearchService;
        this.sectionTimetableService = sectionTimetableService;
        this.engine = engine;
        this.collegeZone = collegeZone;
    }

    @GetMapping("/clock")
    @Operation(summary = "Current college local date and time, used by the dashboard header")
    public ClockResponse clock() {
        ZonedDateTime now = engine.now();
        return new ClockResponse(now.toLocalDate().toString(), TimeText.format(now.toLocalTime()),
                now.getDayOfWeek().name(), collegeZone.getId(), now.toInstant());
    }

    @GetMapping("/student")
    @Operation(summary = "Which class should this student be attending right now")
    public LookupResponse lookupStudentByParameter(
            @Parameter(description = "Registration number, for example UG/02/BTCSEAIML/2023/024")
            @RequestParam(name = "reg") String registrationNumber,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {
        return studentLookupService.lookup(registrationNumber, resolveInstant(at));
    }

    @GetMapping("/student/{*registrationNumber}")
    @Operation(summary = "Which class should this student be attending right now (path form)")
    public LookupResponse lookupStudent(
            @Parameter(description = "Registration number. The catch all form exists because college "
                    + "registration numbers contain slashes; the query parameter form is preferred.")
            @PathVariable String registrationNumber,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {
        return studentLookupService.lookup(stripLeadingSlash(registrationNumber), resolveInstant(at));
    }

    /** Spring's catch all variable keeps the separating slash; the value never has one. */
    private static String stripLeadingSlash(String value) {
        return value == null ? null : value.replaceFirst("^/+", "");
    }

    @GetMapping("/section")
    @Operation(summary = "Look up a section by identifiers or by free text such as "
            + "\"B.Tech CSE AI-ML, semester 5, section D\"")
    public LookupResponse lookupSection(
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) @Min(1) @Max(12) Integer semester,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String academicYear,
            @Parameter(description = "Free text search, for example \"B.Tech CSE, 5th semester, section D\"")
            @RequestParam(required = false) String q,
            @Parameter(description = "Registration number typed alongside a course description")
            @RequestParam(required = false) String reg,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {

        ZonedDateTime instant = resolveInstant(at);

        if (reg != null && !reg.isBlank()) {
            return studentLookupService.lookup(reg, instant);
        }

        SectionSearchService.Resolution resolution;
        if (sectionId != null) {
            resolution = sectionSearchService.resolveById(sectionId);
        } else {
            resolution = sectionSearchService.resolve(programId, semester, section, academicYear, q);
        }

        return switch (resolution.outcome()) {
            case MATCHED -> studentLookupService.lookupSection(resolution.single().sectionId(), instant);
            case AMBIGUOUS -> ambiguous(resolution, instant);
            case NOT_FOUND -> LookupResponse.of(LookupStatus.AMBIGUOUS_SEARCH,
                    resolution.explanation() == null ? "No section matched that search." : resolution.explanation(),
                    instant.toInstant(), collegeZone.getId(), instant.toLocalDate().toString(),
                    TimeText.format(instant.toLocalTime()), instant.getDayOfWeek().name());
        };
    }

    @GetMapping("/section/{sectionId}/week")
    @Operation(summary = "Whole week timetable for one section")
    public SectionTimetableResponse sectionWeek(@PathVariable Long sectionId,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {
        return sectionTimetableService.week(sectionId, resolveInstant(at));
    }

    @GetMapping("/next-class")
    @Operation(summary = "The next scheduled class for a student or a section")
    public NextClassResponse nextClass(@RequestParam(required = false) String reg,
                                       @RequestParam(required = false) Long sectionId,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {
        if ((reg == null || reg.isBlank()) && sectionId == null) {
            throw ApiException.badRequest("LOOKUP_TARGET_REQUIRED",
                    "Provide either a registration number or a section id.");
        }
        ZonedDateTime instant = resolveInstant(at);
        LookupResponse response = reg != null && !reg.isBlank()
                ? studentLookupService.lookup(reg, instant)
                : studentLookupService.lookupSection(sectionId, instant);
        return new NextClassResponse(response.status(), response.nextClass(),
                response.nextClassOnLaterDay(), response.student());
    }

    private LookupResponse ambiguous(SectionSearchService.Resolution resolution, ZonedDateTime instant) {
        List<AmbiguityOption> options = resolution.sections().stream()
                .map(SectionSearchService.ResolvedSection::option)
                .toList();
        return new LookupResponse(LookupStatus.AMBIGUOUS_SEARCH,
                resolution.explanation(), instant.toInstant(), collegeZone.getId(),
                instant.toLocalDate().toString(), TimeText.format(instant.toLocalTime()),
                instant.getDayOfWeek().name(), null, null, null, false, null,
                List.of("The system will not choose a section for you, because the wrong section "
                        + "would give the wrong answer."),
                options, List.of());
    }

    private ZonedDateTime resolveInstant(LocalDateTime at) {
        return at == null ? engine.now() : at.atZone(collegeZone);
    }

    public record ClockResponse(String date, String time, String dayName, String timezone,
                                java.time.Instant instant) {
    }

    public record NextClassResponse(LookupStatus status, ClassInfo nextClass, boolean onLaterDay,
                                    com.college.timetable.dto.lookup.StudentSummary student) {
    }
}