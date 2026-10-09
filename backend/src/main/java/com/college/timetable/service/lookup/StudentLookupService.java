package com.college.timetable.service.lookup;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.college.timetable.dto.lookup.AmbiguityOption;
import com.college.timetable.dto.lookup.LookupResponse;
import com.college.timetable.dto.lookup.LookupStatus;
import com.college.timetable.dto.lookup.StudentSummary;
import com.college.timetable.entity.Section;
import com.college.timetable.entity.Student;
import com.college.timetable.exception.ApiException;
import com.college.timetable.repository.SectionRepository;
import com.college.timetable.repository.StudentRepository;
import com.college.timetable.util.RegistrationNumberNormalizer;

/**
 * Answers "which class should this student be in right now".
 *
 * <p>The only way a student is found is an exact match on the normalised registration number.
 * There is deliberately no fuzzy matching and no fallback that could return a different person.
 */
@Service
public class StudentLookupService {

    private final StudentRepository studentRepository;
    private final SectionRepository sectionRepository;
    private final TimetableLookupEngine engine;

    public StudentLookupService(StudentRepository studentRepository,
                                SectionRepository sectionRepository,
                                TimetableLookupEngine engine) {
        this.studentRepository = studentRepository;
        this.sectionRepository = sectionRepository;
        this.engine = engine;
    }

    /**
     * @param registrationNumber free text from the user; only whitespace and case are normalised
     * @param at                 the instant to evaluate, already in the college timezone
     */
    @Transactional(readOnly = true)
    public LookupResponse lookup(String registrationNumber, ZonedDateTime at) {
        String normalized = RegistrationNumberNormalizer.normalize(registrationNumber);
        if (normalized == null || normalized.isEmpty()) {
            throw ApiException.badRequest("REGISTRATION_REQUIRED",
                    "Enter a registration number to search.");
        }

        Student student = studentRepository.findForLookup(normalized).orElse(null);
        if (student == null) {
            return engine.toResponse(
                    new TimetableLookupEngine.EngineResult(LookupStatus.STUDENT_NOT_FOUND,
                            null, null, false, null, List.of(), List.of()),
                    null, at);
        }

        if (!student.isActive()) {
            return engine.toResponse(
                    new TimetableLookupEngine.EngineResult(LookupStatus.STUDENT_INACTIVE,
                            null, null, false, null, List.of(), List.of()),
                    () -> toSummary(student), at);
        }

        return lookupSection(student.getSection(), student, at);
    }

    /** Timetable lookup for a section without a specific student, used by the section screen. */
    @Transactional(readOnly = true)
    public LookupResponse lookupSection(Long sectionId, ZonedDateTime at) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> ApiException.notFound("Section " + sectionId + " was not found."));
        return lookupSection(section, null, at);
    }

    private LookupResponse lookupSection(Section section, Student student, ZonedDateTime at) {
        TimetableLookupEngine.EngineResult result = engine.resolve(section.getId(), at);
        TimetableLookupEngine.StudentSummaryProvider provider =
                student == null ? null : () -> toSummary(student);
        return engine.toResponse(result, provider, at);
    }

    public static StudentSummary toSummary(Student student) {
        Section section = student.getSection();
        var program = section.getProgram();
        var term = section.getAcademicTerm();
        return new StudentSummary(
                student.getRegistrationNumber(),
                section.getId(),
                student.getFullName(),
                program.getDepartment() == null ? null : program.getDepartment().getName(),
                program.getName(),
                program.getCode(),
                term.getSemesterNumber(),
                term.getAcademicYear(),
                section.getSectionName(),
                student.isActive());
    }

    public static AmbiguityOption toOption(Section section) {
        var program = section.getProgram();
        var term = section.getAcademicTerm();
        return new AmbiguityOption(section.getId(),
                "%s - %s semester %d section %s".formatted(program.getName(),
                        term.getAcademicYear(), term.getSemesterNumber(), section.getSectionName()),
                program.getDepartment() == null ? null : program.getDepartment().getName(),
                program.getName(), program.getCode(), term.getAcademicYear(),
                term.getSemesterNumber(), section.getSectionName());
    }
}