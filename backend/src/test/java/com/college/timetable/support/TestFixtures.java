package com.college.timetable.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.college.timetable.entity.AcademicTerm;
import com.college.timetable.entity.Department;
import com.college.timetable.entity.Faculty;
import com.college.timetable.entity.Program;
import com.college.timetable.entity.Room;
import com.college.timetable.entity.Section;
import com.college.timetable.entity.Student;
import com.college.timetable.entity.Subject;
import com.college.timetable.repository.AcademicTermRepository;
import com.college.timetable.repository.DepartmentRepository;
import com.college.timetable.repository.FacultyRepository;
import com.college.timetable.repository.ProgramRepository;
import com.college.timetable.repository.RoomRepository;
import com.college.timetable.repository.SectionRepository;
import com.college.timetable.repository.StudentRepository;
import com.college.timetable.repository.SubjectRepository;
import com.college.timetable.util.RegistrationNumberNormalizer;

/**
 * Test fixtures with a pinned clock.
 *
 * <p>Every test that asks "what is happening right now" must not depend on the wall clock, or it
 * will start failing at 5pm on a Friday. {@link #FIXED_INSTANT} is a Friday at 10:00 local time,
 * in the middle of the first seeded period.
 */
@TestConfiguration
public class TestFixtures {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    /** 2026-10-09T10:00 local, a Friday, inside the 09:30-10:25 period. */
    public static final Instant FIXED_INSTANT = LocalDate.of(2026, 10, 9)
            .atTime(10, 0).atZone(ZONE).toInstant();

    @Bean
    @Primary
    public Clock fixedClock() {
        return Clock.fixed(FIXED_INSTANT, ZONE);
    }

    public record Catalog(Department department, Program program, AcademicTerm term,
                          Section section, Subject subject, Faculty faculty, Room room) {
    }

    public static Department department(DepartmentRepository repository, String code) {
        Department department = new Department();
        department.setCode(code);
        department.setName("Computer Science and Engineering");
        department.setActive(true);
        return repository.save(department);
    }

    public static Program program(ProgramRepository repository, Department department, String code) {
        Program program = new Program();
        program.setDepartment(department);
        program.setCode(code);
        program.setName(code.equals("BTCSEAIML") ? "B.Tech CSE AI-ML" : "B.Tech CSE");
        program.setDegreeType("UG");
        program.setActive(true);
        return repository.save(program);
    }

    public static AcademicTerm term(AcademicTermRepository repository, String year, int semester,
                                     LocalDate start, LocalDate end) {
        AcademicTerm term = new AcademicTerm();
        term.setAcademicYear(year);
        term.setSemesterNumber(semester);
        term.setStartDate(start);
        term.setEndDate(end);
        term.setActive(true);
        return repository.save(term);
    }

    public static Section section(SectionRepository repository, Program program, AcademicTerm term,
                                  String name) {
        Section section = new Section();
        section.setProgram(program);
        section.setAcademicTerm(term);
        section.setSectionName(name);
        section.setActive(true);
        return repository.save(section);
    }

    public static Subject subject(SubjectRepository repository, Program program, String code, String name) {
        Subject subject = new Subject();
        subject.setSubjectCode(code);
        subject.setSubjectName(name);
        subject.setProgram(program);
        subject.setActive(true);
        return repository.save(subject);
    }

    public static Faculty faculty(FacultyRepository repository, String name, String code) {
        Faculty faculty = new Faculty();
        faculty.setFacultyName(name);
        faculty.setFacultyCode(code);
        faculty.setActive(true);
        return repository.save(faculty);
    }

    public static Room room(RoomRepository repository, String code) {
        Room room = new Room();
        room.setRoomCode(code);
        room.setBuilding("Academic Block 6");
        room.setFloor("4");
        return repository.save(room);
    }

    public static Student student(StudentRepository repository, String registrationNumber, Section section) {
        Student student = new Student();
        student.setRegistrationNumber(registrationNumber);
        student.setRegistrationNumberNormalized(RegistrationNumberNormalizer.normalize(registrationNumber));
        student.setFullName("Test Student");
        student.setSection(section);
        student.setActive(true);
        return repository.save(student);
    }

    /** Builds the whole verified reference graph the lookup engine depends on. */
    public static Catalog catalog(DepartmentRepository departments,
                                  ProgramRepository programs,
                                  AcademicTermRepository terms,
                                  SectionRepository sections,
                                  SubjectRepository subjects,
                                  FacultyRepository faculties,
                                  RoomRepository rooms) {
        Department department = department(departments, "CSE");
        Program program = program(programs, department, "BTCSEAIML");
        AcademicTerm term = term(terms, "2025-26", 5,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 11, 30));
        Section section = section(sections, program, term, "D");
        Subject subject = subject(subjects, program, "CSE11036", "Cloud Computing");
        Faculty faculty = faculty(faculties, "Prof. Amitava Sen", "56312");
        Room room = room(rooms, "AU6-4304");
        return new Catalog(department, program, term, section, subject, faculty, room);
    }

    /** Local instants used by the assertions, derived from the fixed clock so nothing drifts. */
    public static java.time.ZonedDateTime local(int hour, int minute) {
        return LocalDate.of(2026, 10, 9).atTime(hour, minute).atZone(ZONE);
    }

    public static List<Integer> weekdays() {
        return List.of(1, 2, 3, 4, 5, 6);
    }
}