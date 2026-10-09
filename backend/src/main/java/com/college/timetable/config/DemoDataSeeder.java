package com.college.timetable.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.college.timetable.entity.AcademicTerm;
import com.college.timetable.entity.Department;
import com.college.timetable.entity.EntryType;
import com.college.timetable.entity.Faculty;
import com.college.timetable.entity.Program;
import com.college.timetable.entity.Room;
import com.college.timetable.entity.Section;
import com.college.timetable.entity.Student;
import com.college.timetable.entity.Subject;
import com.college.timetable.entity.Timetable;
import com.college.timetable.entity.TimetableEntry;
import com.college.timetable.entity.TimetableStatus;
import com.college.timetable.entity.VerificationStatus;
import com.college.timetable.repository.AcademicTermRepository;
import com.college.timetable.repository.DepartmentRepository;
import com.college.timetable.repository.FacultyRepository;
import com.college.timetable.repository.ProgramRepository;
import com.college.timetable.repository.RoomRepository;
import com.college.timetable.repository.SectionRepository;
import com.college.timetable.repository.StudentRepository;
import com.college.timetable.repository.SubjectRepository;
import com.college.timetable.repository.TimetableEntryRepository;
import com.college.timetable.repository.TimetableRepository;
import com.college.timetable.util.RegistrationNumberNormalizer;

/**
 * Loads clearly labelled synthetic data so the lookup workflow can be exercised before any real
 * college data exists.
 *
 * <p>Every name produced here carries the marker {@code TEST DATA}. These are not real students
 * and must never be loaded into a production database; the runner is off unless
 * {@code SEED_DEMO_DATA=true}.
 */
@Component
@ConditionalOnProperty(name = "college.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String MARKER = "TEST DATA";

    private final DepartmentRepository departmentRepository;
    private final ProgramRepository programRepository;
    private final AcademicTermRepository termRepository;
    private final SectionRepository sectionRepository;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final FacultyRepository facultyRepository;
    private final RoomRepository roomRepository;
    private final TimetableRepository timetableRepository;
    private final TimetableEntryRepository entryRepository;
    private final Clock clock;
    private final ZoneId zone;

    public DemoDataSeeder(DepartmentRepository departmentRepository,
                          ProgramRepository programRepository,
                          AcademicTermRepository termRepository,
                          SectionRepository sectionRepository,
                          StudentRepository studentRepository,
                          SubjectRepository subjectRepository,
                          FacultyRepository facultyRepository,
                          RoomRepository roomRepository,
                          TimetableRepository timetableRepository,
                          TimetableEntryRepository entryRepository,
                          Clock clock,
                          ZoneId zone) {
        this.departmentRepository = departmentRepository;
        this.programRepository = programRepository;
        this.termRepository = termRepository;
        this.sectionRepository = sectionRepository;
        this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
        this.facultyRepository = facultyRepository;
        this.roomRepository = roomRepository;
        this.timetableRepository = timetableRepository;
        this.entryRepository = entryRepository;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (studentRepository.count() > 0) {
            log.info("Demo data seeding skipped: the database already contains students.");
            return;
        }

        LocalDate today = LocalDate.now(clock.withZone(zone));

        Department department = departmentRepository.save(department("CSE", "Computer Science and Engineering (" + MARKER + ")"));
        Program program = programRepository.save(program(department, "BTCSEAIML",
                "B.Tech CSE AI-ML (" + MARKER + ")", "UG"));
        Program programCse = programRepository.save(program(department, "BTCSECSE",
                "B.Tech CSE (" + MARKER + ")", "UG"));

        AcademicTerm term5 = termRepository.save(term("2025-26", 5, today.minusDays(45), today.plusDays(45)));
        AcademicTerm term6 = termRepository.save(term("2025-26", 6, today.plusDays(46), today.plusDays(135)));

        Section sectionD = sectionRepository.save(section(program, term5, "D"));
        Section sectionC = sectionRepository.save(section(program, term5, "C"));
        Section sectionE = sectionRepository.save(section(program, term6, "D"));
        Section cseSectionD = sectionRepository.save(section(programCse, term5, "D"));

        Subject algorithms = subjectRepository.save(subject("CSE11036", "Cloud Computing (TEST DATA)", program));
        Subject nlp = subjectRepository.save(subject("CSE11209", "Natural Language Processing (TEST DATA)", program));
        Subject cryptography = subjectRepository.save(subject("CSE11040", "Cryptography and Cyber Security (TEST DATA)", program));
        Subject lab = subjectRepository.save(subject("CSE12045", "Cloud Computing Lab (TEST DATA)", program));
        Subject minor = subjectRepository.save(subject("CSE14050", "Minor Project (TEST DATA)", program));

        Faculty amitava = facultyRepository.save(faculty("Prof. (Dr.) Amitava Sen (TEST DATA)", "56312"));
        Faculty hirak = facultyRepository.save(faculty("Dr. Hirak Majumdar (TEST DATA)", "56312-B"));

        Room room4304 = roomRepository.save(room("TEST-AU6-4304", "Academic Block 6", "4"));
        Room room2205 = roomRepository.save(room("TEST-AU6-2205", "Academic Block 6", "2"));

        seedPublishedTimetable(sectionD, today, algorithms, nlp, cryptography, minor, lab,
                amitava, hirak, room4304, room2205);
        seedPublishedTimetable(cseSectionD, today, algorithms, nlp, cryptography, minor, lab,
                amitava, hirak, room4304, room2205);
        // A second programme with no timetable at all, so NO_TIMETABLE can be demonstrated.
        log.info("Section {} intentionally has no timetable yet.", sectionC.getId());
        log.info("Section {} belongs to the next semester and has no timetable yet.", sectionE.getId());

        addStudent("TEST/UG/01/BTCSEAIML/2023/001", "Test Student One", sectionD);
        addStudent("TEST/UG/01/BTCSEAIML/2023/002", "Test Student Two", sectionD);
        addStudent("TEST/UG/01/BTCSEAIML/2023/003", "Test Student Three", sectionC);
        addStudent("TEST/UG/01/BTCSECSE/2023/011", "Test Student Four", cseSectionD);

        log.warn("Loaded SYNTHETIC {} into the database. Never use this data in production.",
                MARKER);
        log.warn("Try registration number TEST/UG/01/BTCSEAIML/2023/001");
    }

    /**
     * Creates a published weekly timetable for a section, with a deliberate free period in the
     * middle of the day so the NO_CLASS_NOW path can be demonstrated.
     */
    private void seedPublishedTimetable(Section section,
                                        LocalDate today,
                                        Subject algorithms, Subject nlp, Subject cryptography,
                                        Subject minor, Subject lab,
                                        Faculty amitava, Faculty hirak,
                                        Room room4304, Room room2205) {
        Timetable timetable = new Timetable();
        timetable.setSection(section);
        timetable.setAcademicTerm(section.getAcademicTerm());
        timetable.setEffectiveFrom(today.minusDays(30));
        timetable.setEffectiveTo(null);
        timetable.setStatus(TimetableStatus.PUBLISHED);
        timetable.setVersion(1);
        timetable.setSourceFilename("synthetic-demo-timetable");
        Timetable saved = timetableRepository.save(timetable);

        List<LocalTime[]> slots = List.of(
                new LocalTime[]{LocalTime.of(9, 30), LocalTime.of(10, 25)},
                new LocalTime[]{LocalTime.of(10, 30), LocalTime.of(11, 25)},
                new LocalTime[]{LocalTime.of(11, 30), LocalTime.of(12, 25)},
                new LocalTime[]{LocalTime.of(12, 30), LocalTime.of(13, 25)},
                new LocalTime[]{LocalTime.of(13, 30), LocalTime.of(14, 25)});

        // Monday to Saturday; slot 3 is deliberately left free on Wednesday and Thursday.
        for (int day = 1; day <= 6; day++) {
            boolean leaveMiddleFree = day == 3 || day == 4;
            for (int slot = 0; slot < slots.size(); slot++) {
                if (leaveMiddleFree && slot == 2) {
                    continue;
                }
                TimetableEntry entry = new TimetableEntry();
                entry.setTimetable(saved);
                entry.setSection(section);
                entry.setStartTime(slots.get(slot)[0]);
                entry.setEndTime(slots.get(slot)[1]);
                entry.setDayOfWeek(day);
                entry.setEntryType(EntryType.CLASS);
                entry.setVerificationStatus(VerificationStatus.VERIFIED);
                entry.setRawSourceText("Synthetic " + MARKER);
                entry.setSubject(subjectForSlot(slot, algorithms, nlp, cryptography, minor, lab));
                entry.setFaculty(day % 2 == 0 ? hirak : amitava);
                entry.setRoom(slot % 2 == 0 ? room4304 : room2205);
                entryRepository.save(entry);
            }
        }
    }

    private static Subject subjectForSlot(int slot, Subject algorithms, Subject nlp,
                                          Subject cryptography, Subject minor, Subject lab) {
        return switch (slot) {
            case 0 -> algorithms;
            case 1 -> nlp;
            case 2 -> cryptography;
            case 3 -> minor;
            default -> lab;
        };
    }

    private void addStudent(String registrationNumber, String name, Section section) {
        Student student = new Student();
        student.setRegistrationNumber(registrationNumber);
        student.setRegistrationNumberNormalized(RegistrationNumberNormalizer.normalize(registrationNumber));
        student.setFullName(name + " (" + MARKER + ")");
        student.setSection(section);
        student.setActive(true);
        studentRepository.save(student);
    }

    private static Department department(String code, String name) {
        Department department = new Department();
        department.setCode(code);
        department.setName(name);
        department.setActive(true);
        return department;
    }

    private static Program program(Department department, String code, String name, String degreeType) {
        Program program = new Program();
        program.setDepartment(department);
        program.setCode(code);
        program.setName(name);
        program.setDegreeType(degreeType);
        program.setActive(true);
        return program;
    }

    private static AcademicTerm term(String year, int semester, LocalDate start, LocalDate end) {
        AcademicTerm term = new AcademicTerm();
        term.setAcademicYear(year);
        term.setSemesterNumber(semester);
        term.setStartDate(start);
        term.setEndDate(end);
        term.setActive(true);
        return term;
    }

    private static Section section(Program program, AcademicTerm term, String name) {
        Section section = new Section();
        section.setProgram(program);
        section.setAcademicTerm(term);
        section.setSectionName(name);
        section.setActive(true);
        return section;
    }

    private static Subject subject(String code, String name, Program program) {
        Subject subject = new Subject();
        subject.setSubjectCode(code);
        subject.setSubjectName(name);
        subject.setProgram(program);
        subject.setActive(true);
        return subject;
    }

    private static Faculty faculty(String name, String code) {
        Faculty faculty = new Faculty();
        faculty.setFacultyName(name);
        faculty.setFacultyCode(code);
        faculty.setActive(true);
        return faculty;
    }

    private static Room room(String code, String building, String floor) {
        Room room = new Room();
        room.setRoomCode(code);
        room.setBuilding(building);
        room.setFloor(floor);
        return room;
    }
}