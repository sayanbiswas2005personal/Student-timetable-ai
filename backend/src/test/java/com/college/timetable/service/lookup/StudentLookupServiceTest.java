package com.college.timetable.service.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.college.timetable.dto.lookup.LookupStatus;
import com.college.timetable.entity.AcademicTerm;
import com.college.timetable.entity.Department;
import com.college.timetable.entity.Program;
import com.college.timetable.entity.Section;
import com.college.timetable.entity.Student;
import com.college.timetable.exception.ApiException;
import com.college.timetable.repository.SectionRepository;
import com.college.timetable.repository.StudentRepository;
import com.college.timetable.repository.TimetableEntryRepository;

class StudentLookupServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final ZonedDateTime NOW =
            ZonedDateTime.of(LocalDate.of(2026, 10, 9), java.time.LocalTime.of(10, 0), ZONE);

    private StudentRepository studentRepository;
    private SectionRepository sectionRepository;
    private TimetableEntryRepository entryRepository;
    private TimetableSelectionService selectionService;
    private StudentLookupService service;

    @BeforeEach
    void setUp() {
        studentRepository = mock(StudentRepository.class);
        sectionRepository = mock(SectionRepository.class);
        entryRepository = mock(TimetableEntryRepository.class);
        selectionService = mock(TimetableSelectionService.class);
        TimetableLookupEngine engine =
                new TimetableLookupEngine(entryRepository, selectionService, Clock.systemUTC(), ZONE);
        service = new StudentLookupService(studentRepository, sectionRepository, engine);

        lenient().when(entryRepository.findDayWithRelations(anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        lenient().when(entryRepository.findAllWithRelations(anyLong())).thenReturn(List.of());
        lenient().when(selectionService.selectForDate(anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new TimetableSelectionService.Selection(
                        Optional.empty(), TimetableSelectionService.MissingReason.NO_ROWS, List.of()));
    }

    private static Student student(String registrationNumber, boolean active) {
        Department department = new Department();
        department.setName("Computer Science and Engineering");
        department.setCode("CSE");
        Program program = new Program();
        program.setId(2L);
        program.setName("B.Tech CSE AI-ML");
        program.setCode("BTCSEAIML");
        program.setDepartment(department);
        AcademicTerm term = new AcademicTerm();
        term.setId(3L);
        term.setAcademicYear("2025-26");
        term.setSemesterNumber(5);
        Section section = new Section();
        section.setId(4L);
        section.setSectionName("D");
        section.setProgram(program);
        section.setAcademicTerm(term);

        Student student = new Student();
        student.setId(5L);
        student.setRegistrationNumber(registrationNumber);
        student.setRegistrationNumberNormalized(registrationNumber.toUpperCase(java.util.Locale.ROOT));
        student.setFullName("Test Student");
        student.setSection(section);
        student.setActive(active);
        return student;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UG/02/BTCSEAIML/2023/024",
            "  ug/02/btcseaiml/2023/024  ",
            "UG/02/BTCSEAIML/2023/024 "
    })
    @DisplayName("cosmetic differences in the typed registration number still match exactly one student")
    void findsStudentDespiteCosmeticDifferences(String typed) {
        when(studentRepository.findForLookup("UG/02/BTCSEAIML/2023/024"))
                .thenReturn(Optional.of(student("UG/02/BTCSEAIML/2023/024", true)));
        lenient().when(sectionRepository.findById(4L)).thenReturn(Optional.empty());

        var response = service.lookup(typed, NOW);

        assertThat(response.status()).isNotEqualTo(LookupStatus.STUDENT_NOT_FOUND);
        assertThat(response.student().registrationNumber()).isEqualTo("UG/02/BTCSEAIML/2023/024");
        assertThat(response.student().programCode()).isEqualTo("BTCSEAIML");
        assertThat(response.student().semesterNumber()).isEqualTo(5);
        assertThat(response.student().sectionName()).isEqualTo("D");
    }

    @Test
    @DisplayName("an unknown registration number is reported plainly, with no partial match")
    void unknownStudent() {
        when(studentRepository.findForLookup("UG/02/BTCSEAIML/2023/999")).thenReturn(Optional.empty());

        var response = service.lookup("UG/02/BTCSEAIML/2023/999", NOW);

        assertThat(response.status()).isEqualTo(LookupStatus.STUDENT_NOT_FOUND);
        assertThat(response.student()).isNull();
        assertThat(response.currentClass()).isNull();
    }

    @Test
    @DisplayName("a near miss is not fuzzy matched to a real student")
    void doesNotFuzzyMatch() {
        when(studentRepository.findForLookup("UG/02/BTCSEAIML/2023/02"))
                .thenReturn(Optional.empty());

        var response = service.lookup("UG/02/BTCSEAIML/2023/02", NOW);

        assertThat(response.status()).isEqualTo(LookupStatus.STUDENT_NOT_FOUND);
    }

    @Test
    @DisplayName("a deactivated student is reported as inactive rather than as missing")
    void inactiveStudent() {
        when(studentRepository.findForLookup("UG/02/BTCSEAIML/2023/024"))
                .thenReturn(Optional.of(student("UG/02/BTCSEAIML/2023/024", false)));

        var response = service.lookup("UG/02/BTCSEAIML/2023/024", NOW);

        assertThat(response.status()).isEqualTo(LookupStatus.STUDENT_INACTIVE);
        assertThat(response.student().active()).isFalse();
    }

    @Test
    @DisplayName("a blank registration number is a request error, not a lookup result")
    void blankRegistrationNumber() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> service.lookup("   ", NOW))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("REGISTRATION_REQUIRED"));
    }

    @Test
    @DisplayName("the evaluated instant is reported in the college timezone")
    void reportsCollegeTime() {
        when(studentRepository.findForLookup("UG/02/BTCSEAIML/2023/024"))
                .thenReturn(Optional.of(student("UG/02/BTCSEAIML/2023/024", true)));

        var response = service.lookup("UG/02/BTCSEAIML/2023/024", NOW);

        assertThat(response.collegeTimezone()).isEqualTo("Asia/Kolkata");
        assertThat(response.collegeDate()).isEqualTo("2026-10-09");
        assertThat(response.collegeTime()).isEqualTo("10:00");
        assertThat(response.dayName()).isEqualTo("FRIDAY");
        assertThat(response.evaluatedAt()).isEqualTo(Instant.parse("2026-10-09T04:30:00Z"));
    }
}