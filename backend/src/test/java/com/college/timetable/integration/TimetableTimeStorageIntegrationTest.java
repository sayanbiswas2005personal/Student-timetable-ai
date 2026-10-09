package com.college.timetable.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.jdbc.core.JdbcTemplate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.college.timetable.entity.AcademicTerm;
import com.college.timetable.entity.Department;
import com.college.timetable.entity.Program;
import com.college.timetable.entity.Section;
import com.college.timetable.entity.Timetable;
import com.college.timetable.entity.TimetableEntry;
import com.college.timetable.entity.TimetableStatus;
import com.college.timetable.repository.AcademicTermRepository;
import com.college.timetable.repository.DepartmentRepository;
import com.college.timetable.repository.ProgramRepository;
import com.college.timetable.repository.SectionRepository;
import com.college.timetable.repository.TimetableEntryRepository;
import com.college.timetable.repository.TimetableRepository;
import com.college.timetable.support.TestFixtures;

/**
 * Guards a bug that no other test could see.
 *
 * <p>With {@code hibernate.jdbc.time_zone} set, Hibernate rebinds {@link LocalTime} values as UTC
 * instants on the way to the database. That is invisible in Java, because the same shift is undone
 * when the value is read back, so every service-level test passed. But the bytes in the column were
 * wrong: a 09:30 class was stored as 04:00, and any consumer that did not repeat the same round
 * trip - a report, a spreadsheet export, another tool - read the wrong time.
 *
 * <p>The test therefore asserts against the column itself, through plain JDBC, rather than through
 * the entity. Wall clock times are not instants and must never be shifted.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TimetableTimeStorageIntegrationTest {

    /**
     * Joins the test transaction, so the row written through the repository is the row read here.
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TimetableEntryRepository entryRepository;

    @Autowired
    private TimetableRepository timetableRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private AcademicTermRepository termRepository;

    @Autowired
    private SectionRepository sectionRepository;

    @Test
    @DisplayName("a class start time is stored as the wall clock time printed in the timetable")
    void storesWallClockTimeUnshifted() throws Exception {
        Section section = section();
        TimetableEntry entry = new TimetableEntry();
        entry.setTimetable(timetable(section));
        entry.setSection(section);
        entry.setDayOfWeek(1);
        entry.setStartTime(LocalTime.of(9, 30));
        entry.setEndTime(LocalTime.of(10, 25));
        entryRepository.save(entry);
        entryRepository.flush();

        String stored = jdbcTemplate.queryForObject(
                "SELECT CAST(start_time AS VARCHAR) FROM timetable_entries WHERE id = ?",
                String.class, entry.getId());

        assertThat(stored).startsWith("09:30:00");
    }

    /** Reuses whatever the demo seeder already made, so this test adds nothing of its own. */
    private Section section() {
        Program program = programRepository.findAll().stream()
                .filter(p -> p.getDepartment() != null && "CSE".equals(p.getDepartment().getCode()))
                .findFirst()
                .orElseGet(() -> TestFixtures.program(programRepository,
                        departmentRepository.findAll().get(0), "TIMESTORAGE"));
        AcademicTerm term = termRepository
                .findByAcademicYearAndSemesterNumber("2099-30", 3)
                .orElseGet(() -> TestFixtures.term(termRepository, "2099-30", 3,
                        LocalDate.of(2026, 7, 20), LocalDate.of(2027, 6, 30)));
        return TestFixtures.section(sectionRepository, program, term, "TS");
    }

    private Timetable timetable(Section section) {
        Timetable timetable = new Timetable();
        timetable.setSection(section);
        timetable.setAcademicTerm(section.getAcademicTerm());
        timetable.setVersion(1);
        timetable.setStatus(TimetableStatus.PUBLISHED);
        timetable.setEffectiveFrom(LocalDate.of(2026, 7, 20));
        timetable.setEffectiveTo(LocalDate.of(2027, 6, 30));
        timetable.setSourceFilename("test.pdf");
        return timetableRepository.save(timetable);
    }
}