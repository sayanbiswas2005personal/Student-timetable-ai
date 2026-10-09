package com.college.timetable.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.college.timetable.dto.lookup.LookupStatus;
import com.college.timetable.dto.timetable.CreateTimetableRequest;
import com.college.timetable.dto.timetable.TimetableEntryRequest;
import com.college.timetable.entity.Section;
import com.college.timetable.entity.Student;
import com.college.timetable.entity.Timetable;
import com.college.timetable.entity.TimetableStatus;
import com.college.timetable.repository.AcademicTermRepository;
import com.college.timetable.repository.DepartmentRepository;
import com.college.timetable.repository.FacultyRepository;
import com.college.timetable.repository.ProgramRepository;
import com.college.timetable.repository.RoomRepository;
import com.college.timetable.repository.SectionRepository;
import com.college.timetable.repository.StudentRepository;
import com.college.timetable.repository.SubjectRepository;
import com.college.timetable.repository.TimetableRepository;
import com.college.timetable.service.lookup.StudentLookupService;
import com.college.timetable.service.timetable.TimetableService;
import com.college.timetable.support.TestFixtures;
import com.college.timetable.support.TestFixtures.Catalog;

/**
 * The complete workflow the system exists for, exercised end to end against the real database:
 *
 * <ol>
 *   <li>create a programme, academic term and section;</li>
 *   <li>enrol a student with a verified section;</li>
 *   <li>create a draft timetable and add periods;</li>
 *   <li>publish it;</li>
 *   <li>look the student up while a class is running;</li>
 *   <li>look the same student up during a free period;</li>
 *   <li>look up a registration number that does not exist.</li>
 * </ol>
 *
 * <p>Every instant is explicit, so the assertions are the same on any day of the week.
 */
@SpringBootTest(properties = "college.seed-demo-data=false")
@ActiveProfiles("test")
@Import(TestFixtures.class)
@Transactional
class TimetableLookupIntegrationTest {

    private static final String REGISTRATION = "UG/02/BTCSEAIML/2023/024";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    @Autowired private DepartmentRepository departments;
    @Autowired private ProgramRepository programs;
    @Autowired private AcademicTermRepository terms;
    @Autowired private SectionRepository sections;
    @Autowired private StudentRepository students;
    @Autowired private SubjectRepository subjects;
    @Autowired private FacultyRepository faculties;
    @Autowired private RoomRepository rooms;
    @Autowired private TimetableRepository timetables;

    @Autowired private TimetableService timetableService;
    @Autowired private StudentLookupService lookupService;

    private Catalog catalog;
    private Student student;
    private Timetable published;

    @BeforeEach
    void setUp() {
        catalog = TestFixtures.catalog(departments, programs, terms, sections, subjects, faculties, rooms);
        student = TestFixtures.student(students, REGISTRATION, catalog.section());
        published = createAndPublishTimetable(catalog);
    }

    private Timetable createAndPublishTimetable(Catalog catalog) {
        var draft = timetableService.createDraft(new CreateTimetableRequest(
                catalog.section().getId(), TODAY.minusDays(7), null, "integration-test.pdf"));

        // Slot 1: 09:30-10:25, slot 2: 10:30-11:25. Friday (day 5) is deliberately left empty
        // after 11:25 so the free period path has something real to report.
        timetableService.upsertEntry(draft.id(), null, entry(
                5, LocalTime.of(9, 30), LocalTime.of(10, 25), catalog));
        timetableService.upsertEntry(draft.id(), null, entry(
                5, LocalTime.of(10, 30), LocalTime.of(11, 25), catalog));
        // Monday also has periods, so a Friday free period can report a next class on a later day.
        timetableService.upsertEntry(draft.id(), null, entry(
                1, LocalTime.of(9, 30), LocalTime.of(10, 25), catalog));

        return timetableService.publish(draft.id(), null) == null ? null : findDraft(draft.id());
    }

    private Timetable findDraft(Long id) {
        return timetables.findById(id).orElseThrow();
    }

    private TimetableEntryRequest entry(int day, LocalTime start, LocalTime end, Catalog catalog) {
        return new TimetableEntryRequest(day, start, end,
                catalog.subject().getId(), catalog.faculty().getId(), catalog.room().getId(),
                "CLASS", "integration test row");
    }

    @Test
    @DisplayName("the published timetable answers a lookup during a scheduled class")
    void lookupDuringClass() {
        var response = lookupService.lookup(REGISTRATION, TestFixtures.local(10, 0));

        assertThat(response.status()).isEqualTo(LookupStatus.CLASS_IN_PROGRESS);
        assertThat(response.student().registrationNumber()).isEqualTo(REGISTRATION);
        assertThat(response.student().programCode()).isEqualTo("BTCSEAIML");
        assertThat(response.student().semesterNumber()).isEqualTo(5);
        assertThat(response.student().sectionName()).isEqualTo("D");
        assertThat(response.currentClass().subjectCode()).isEqualTo("CSE11036");
        assertThat(response.currentClass().subjectName()).isEqualTo("Cloud Computing");
        assertThat(response.currentClass().roomCode()).isEqualTo("AU6-4304");
        assertThat(response.currentClass().facultyName()).isEqualTo("Prof. Amitava Sen");
        assertThat(response.currentClass().startTime()).isEqualTo("09:30");
        assertThat(response.currentClass().endTime()).isEqualTo("10:25");
        assertThat(response.timetable().version()).isEqualTo(1);
    }

    @Test
    @DisplayName("the same student during the free period gets no class and the next one")
    void lookupDuringFreePeriod() {
        var response = lookupService.lookup(REGISTRATION, TestFixtures.local(12, 0));

        assertThat(response.status()).isEqualTo(LookupStatus.NO_CLASS_NOW);
        assertThat(response.currentClass()).isNull();
        assertThat(response.student()).isNotNull();
        // Nothing later today, so the answer rolls over to the next weekday with a period.
        assertThat(response.nextClass()).isNotNull();
        assertThat(response.nextClassOnLaterDay()).isTrue();
    }

    @Test
    @DisplayName("a gap between two periods reports the second one as next")
    void lookupInTheGap() {
        var response = lookupService.lookup(REGISTRATION, TestFixtures.local(10, 28));

        assertThat(response.status()).isEqualTo(LookupStatus.NO_CLASS_NOW);
        assertThat(response.nextClass().startTime()).isEqualTo("10:30");
        assertThat(response.nextClassOnLaterDay()).isFalse();
    }

    @Test
    @DisplayName("the exact end time is outside the class because intervals are half open")
    void lookupAtExactEnd() {
        var response = lookupService.lookup(REGISTRATION, TestFixtures.local(10, 25));

        assertThat(response.status()).isEqualTo(LookupStatus.NO_CLASS_NOW);
        assertThat(response.nextClass().startTime()).isEqualTo("10:30");
    }

    @Test
    @DisplayName("a registration number that does not exist is reported, never guessed at")
    void unknownRegistrationNumber() {
        var response = lookupService.lookup("UG/02/BTCSEAIML/2023/999", TestFixtures.local(10, 0));

        assertThat(response.status()).isEqualTo(LookupStatus.STUDENT_NOT_FOUND);
        assertThat(response.student()).isNull();
        assertThat(response.currentClass()).isNull();
        assertThat(response.nextClass()).isNull();
    }

    @Test
    @DisplayName("the message never implies that a student did anything wrong")
    void messageIsNeutral() {
        String duringClass = lookupService.lookup(REGISTRATION, TestFixtures.local(10, 0)).message();
        String freePeriod = lookupService.lookup(REGISTRATION, TestFixtures.local(12, 0)).message();
        String notFound = lookupService.lookup("NOPE/1", TestFixtures.local(10, 0)).message();

        for (String message : List.of(duringClass, freePeriod, notFound)) {
            assertThat(message.toLowerCase())
                    .doesNotContain("absent")
                    .doesNotContain("bunk")
                    .doesNotContain("absentee")
                    .doesNotContain("violat")
                    .doesNotContain("missing")
                    .doesNotContain("penal");
        }
    }

    @Test
    @DisplayName("a lookup with no timetable says so instead of returning an empty answer")
    void sectionWithoutTimetable() {
        Section other = TestFixtures.section(sections, catalog.program(), catalog.term(), "C");
        TestFixtures.student(students, "UG/02/BTCSEAIML/2023/777", other);

        var response = lookupService.lookup("UG/02/BTCSEAIML/2023/777", TestFixtures.local(10, 0));

        assertThat(response.status()).isEqualTo(LookupStatus.NO_TIMETABLE);
        assertThat(response.student().sectionName()).isEqualTo("C");
    }

    @Test
    @DisplayName("a draft that was never published is not used by the lookup engine")
    void unpublishedDraftIsNotUsed() {
        timetableService.createDraft(new CreateTimetableRequest(
                catalog.section().getId(), TODAY, null, "draft-v2.pdf"));

        var response = lookupService.lookup(REGISTRATION, TestFixtures.local(10, 0));

        assertThat(response.status()).isEqualTo(LookupStatus.CLASS_IN_PROGRESS);
        assertThat(response.timetable().version()).isEqualTo(1);
    }

    @Test
    @DisplayName("publishing marks the version PUBLISHED and records who did it")
    void publishingIsRecorded() {
        assertThat(published.getStatus()).isEqualTo(TimetableStatus.PUBLISHED);
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("a lookup outside the effective range reports no timetable for that date")
    void outsideEffectiveRange() {
        Section outside = TestFixtures.section(sections, catalog.program(), catalog.term(), "E");
        TestFixtures.student(students, "UG/02/BTCSEAIML/2023/888", outside);
        var draft = timetableService.createDraft(new CreateTimetableRequest(
                outside.getId(), TODAY.plusDays(30), null, "future.pdf"));
        timetableService.upsertEntry(draft.id(), null, entry(
                5, LocalTime.of(9, 30), LocalTime.of(10, 25), catalog));
        timetableService.publish(draft.id(), null);

        var response = lookupService.lookup("UG/02/BTCSEAIML/2023/888", TestFixtures.local(10, 0));

        assertThat(response.status()).isEqualTo(LookupStatus.NO_TIMETABLE);
    }

    @Test
    @DisplayName("a second published version takes over from its effective date")
    void newVersionTakesOver() {
        // Effective from the following Friday, so the lookup date below is also a Friday.
        LocalDate secondStart = TODAY.plusDays(7);
        var second = timetableService.createDraft(new CreateTimetableRequest(
                catalog.section().getId(), secondStart, null, "v2.pdf"));
        timetableService.upsertEntry(second.id(), null, entry(
                5, LocalTime.of(14, 0), LocalTime.of(15, 0), catalog));
        timetableService.publish(second.id(), null);

        var before = lookupService.lookup(REGISTRATION, TestFixtures.local(10, 0));
        var after = lookupService.lookup(REGISTRATION,
                secondStart.atTime(14, 30).atZone(TestFixtures.ZONE));

        assertThat(before.timetable().version()).isEqualTo(1);
        assertThat(before.currentClass().startTime()).isEqualTo("09:30");
        assertThat(after.timetable().version()).isEqualTo(2);
        assertThat(after.currentClass().startTime()).isEqualTo("14:00");
    }

    @Test
    @DisplayName("publishing closes the previous version but keeps it for historical lookup")
    void previousVersionRemainsReadable() {
        LocalDate secondStart = TODAY.plusDays(7);
        var second = timetableService.createDraft(new CreateTimetableRequest(
                catalog.section().getId(), secondStart, null, "v2.pdf"));
        timetableService.upsertEntry(second.id(), null, entry(
                5, LocalTime.of(14, 0), LocalTime.of(15, 0), catalog));
        var publishedSecond = timetableService.publish(second.id(), null);

        var historical = lookupService.lookup(REGISTRATION, TestFixtures.local(10, 0));

        assertThat(historical.timetable().version()).isEqualTo(1);
        assertThat(historical.timetable().effectiveTo()).isEqualTo(secondStart.minusDays(1));
        assertThat(publishedSecond.version()).isEqualTo(2);
    }
}
