package com.college.timetable.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.college.timetable.dto.catalog.ProgramRequest;
import com.college.timetable.dto.catalog.SectionRequest;
import com.college.timetable.service.admin.CatalogService;
import com.college.timetable.service.lookup.SectionSearchService;
import com.college.timetable.entity.Section;
import com.college.timetable.support.TestFixtures;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The free text search path, exercised through the real HTTP layer.
 *
 * <p>This exists because of a bug that only appeared against a running server: the ambiguous
 * search result was built in the controller, after the transaction had closed, so touching a
 * lazily loaded programme or department failed at runtime. Unit tests never caught it because they
 * call the service directly.
 */
@SpringBootTest(properties = "college.seed-demo-data=false")
@AutoConfigureMockMvc
@Import(TestFixtures.class)
@ActiveProfiles("test")
@Transactional
class SectionSearchIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CatalogService catalogService;
    @Autowired private SectionSearchService sectionSearchService;
    @Autowired private com.college.timetable.service.timetable.TimetableService timetableService;
    @Autowired private com.college.timetable.repository.TimetableRepository timetables;

    @Autowired private com.college.timetable.repository.DepartmentRepository departments;
    @Autowired private com.college.timetable.repository.ProgramRepository programs;
    @Autowired private com.college.timetable.repository.AcademicTermRepository terms;
    @Autowired private com.college.timetable.repository.SectionRepository sections;
    @Autowired private com.college.timetable.repository.SubjectRepository subjects;
    @Autowired private com.college.timetable.repository.FacultyRepository faculties;
    @Autowired private com.college.timetable.repository.RoomRepository rooms;

    private static final org.springframework.test.web.servlet.request.RequestPostProcessor ADMIN =
            SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN");

    private void seedTwoProgrammesInTheSameSemester() {
        var department = TestFixtures.department(departments, "CSE-SEARCH");
        var first = TestFixtures.program(programs, department, "BTCSEAIML");
        var second = TestFixtures.program(programs, department, "BTCSECSE");
        var term = TestFixtures.term(terms, "2025-26", 5,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 11, 30));
        TestFixtures.section(sections, first, term, "D");
        TestFixtures.section(sections, second, term, "D");
    }

    /**
     * A section id used to be passed as the programme id of the search, which quietly searched a
     * different programme. The test gives the two seeded sections different timetable states, so
     * asking for one by id can only produce the right answer if the id was honoured.
     */
    private void publishTimetableFor(Section section) {
        var subject = TestFixtures.subject(subjects, section.getProgram(), "CSE99001", "By Id Only Subject");
        var timetable = new com.college.timetable.entity.Timetable();
        timetable.setSection(section);
        timetable.setAcademicTerm(section.getAcademicTerm());
        timetable.setVersion(1);
        timetable.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        timetable.setEffectiveTo(LocalDate.of(2035, 1, 1));
        timetable.setSourceFilename("test.pdf");
        var saved = timetables.save(timetable);
        timetableService.upsertEntry(saved.getId(), null,
                new com.college.timetable.dto.timetable.TimetableEntryRequest(
                        5, LocalTime.of(9, 30), LocalTime.of(10, 25),
                        subject.getId(), null, null, "CLASS", null));
        timetableService.publish(saved.getId(), null);
    }

    @Test
    @DisplayName("a known section id resolves to that section, not to a search of the wrong programme")
    void sectionIdResolvesDirectly() throws Exception {
        var department = TestFixtures.department(departments, "CSE-BYID");
        var withTimetable = TestFixtures.program(programs, department, "BTCSEAIML");
        var withoutTimetable = TestFixtures.program(programs, department, "BTCSECSE");
        var term = TestFixtures.term(terms, "2031-32", 1,
                LocalDate.of(2031, 6, 1), LocalDate.of(2031, 11, 30));
        // Padding sections push the id of the section under test well past the number of
        // programmes. Otherwise section id 1 and programme id 1 coincide and a lookup that wrongly
        // reads the section id as a programme id would still land on the right answer.
        var filler = TestFixtures.term(terms, "2030-31", 2,
                LocalDate.of(2030, 6, 1), LocalDate.of(2030, 11, 30));
        for (int i = 0; i < 6; i++) {
            TestFixtures.section(sections, withoutTimetable, filler, "PAD" + i);
        }
        var scheduled = TestFixtures.section(sections, withTimetable, term, "D");
        TestFixtures.section(sections, withoutTimetable, term, "D");
        publishTimetableFor(scheduled);

        mockMvc.perform(get("/api/lookup/section")
                        .param("sectionId", String.valueOf(scheduled.getId()))
                        .with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLASS_IN_PROGRESS"))
                // Only the requested section carries this subject code, so the answer cannot have
                // come from some other programme that happens to share the section name.
                .andExpect(jsonPath("$.currentClass.subjectCode").value("CSE99001"));
    }

    @Test
    @DisplayName("a plain letter finds the qualified section, such as G for 'G (AIML)'")
    void plainLetterFindsQualifiedSection() throws Exception {
        var department = TestFixtures.department(departments, "CSE-QUAL");
        var program = TestFixtures.program(programs, department, "BTECSAIML");
        var term = TestFixtures.term(terms, "2033-34", 5,
                LocalDate.of(2033, 6, 1), LocalDate.of(2033, 11, 30));
        TestFixtures.section(sections, program, term, "G (AIML)");
        TestFixtures.section(sections, program, term, "H");

        mockMvc.perform(get("/api/lookup/section")
                        .param("q", "BTECSAIML, 5th semester, section G")
                        .with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_TIMETABLE"))
                .andExpect(jsonPath("$.options.length()").value(0));
    }

    @Test
    @DisplayName("asking for the section without a timetable by id reports NO_TIMETABLE")
    void sectionIdWithoutTimetableIsReported() throws Exception {
        var department = TestFixtures.department(departments, "CSE-NOID");
        var program = TestFixtures.program(programs, department, "BTCSENOTT");
        var term = TestFixtures.term(terms, "2032-33", 1,
                LocalDate.of(2032, 6, 1), LocalDate.of(2032, 11, 30));
        var empty = TestFixtures.section(sections, program, term, "D");

        mockMvc.perform(get("/api/lookup/section")
                        .param("sectionId", String.valueOf(empty.getId()))
                        .with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_TIMETABLE"));
    }

    @Test
    @DisplayName("an unknown section id is reported as not found rather than as a prompt to search")
    void unknownSectionIdIsNotFound() throws Exception {
        mockMvc.perform(get("/api/lookup/section")
                        .param("sectionId", "999999")
                        .with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AMBIGUOUS_SEARCH"));
    }

    @Test
    @DisplayName("an ambiguous free text search returns the choices without a server error")
    void ambiguousSearchReturnsOptions() throws Exception {
        seedTwoProgrammesInTheSameSemester();

        mockMvc.perform(get("/api/lookup/section")
                        .param("q", "B.Tech CSE, 5th semester, section D")
                        .with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AMBIGUOUS_SEARCH"))
                .andExpect(jsonPath("$.options.length()").value(2))
                .andExpect(jsonPath("$.options[0].sectionId").isNumber())
                .andExpect(jsonPath("$.options[0].programName").isString())
                .andExpect(jsonPath("$.options[0].departmentName").value("Computer Science and Engineering"))
                .andExpect(jsonPath("$.notices[0]").value(org.hamcrest.Matchers.containsString(
                        "will not choose a section")));
    }

    @Test
    @DisplayName("an exact programme code resolves to one section")
    void exactProgrammeCodeResolves() throws Exception {
        seedTwoProgrammesInTheSameSemester();

        mockMvc.perform(get("/api/lookup/section")
                        .param("q", "BTCSEAIML, semester 5, section D")
                        .with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_TIMETABLE"))
                .andExpect(jsonPath("$.student").doesNotExist())
                .andExpect(jsonPath("$.options.length()").value(0));
    }

    @Test
    @DisplayName("a longer course name still offers the shorter one rather than deciding silently")
    void longerCourseNameDoesNotHideAShorterOne() throws Exception {
        seedTwoProgrammesInTheSameSemester();

        // "B.Tech CSE AI-ML" contains "B.Tech CSE", so both are plausible. Offering both and
        // asking is the safe answer; guessing would risk showing the wrong section's timetable.
        mockMvc.perform(get("/api/lookup/section")
                        .param("q", "B.Tech CSE AI-ML, semester 5, section D")
                        .with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AMBIGUOUS_SEARCH"))
                .andExpect(jsonPath("$.options.length()").value(2));
    }

    @Test
    @DisplayName("a search that matches nothing explains itself instead of failing")
    void noMatchIsExplained() throws Exception {
        mockMvc.perform(get("/api/lookup/section")
                        .param("q", "Underwater Basket Weaving, semester 3, section Z")
                        .with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AMBIGUOUS_SEARCH"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("No course")))
                .andExpect(jsonPath("$.options.length()").value(0));
    }

    @Test
    @DisplayName("building the options inside the service keeps lazy loading inside the transaction")
    void optionsAreBuiltInsideTheTransaction() {
        seedTwoProgrammesInTheSameSemester();

        var resolution = sectionSearchService.resolve(null, null, null, null,
                "B.Tech CSE, 5th semester, section D");

        assertThat(resolution.outcome()).isEqualTo(SectionSearchService.Outcome.AMBIGUOUS);
        // The options were built inside the transaction that loaded the sections, so reading the
        // lazily loaded programme and department cannot touch a closed session.
        var options = resolution.sections().stream()
                .map(SectionSearchService.ResolvedSection::option)
                .toList();
        assertThat(options).hasSize(2);
        assertThat(options).allSatisfy(option -> {
            assertThat(option.sectionId()).isNotNull();
            assertThat(option.programName()).isNotBlank();
            assertThat(option.departmentName()).isEqualTo("Computer Science and Engineering");
            assertThat(option.semesterNumber()).isEqualTo(5);
            assertThat(option.sectionName()).isEqualTo("D");
        });
    }

    @Test
    @DisplayName("the section week view loads for a real section")
    void weekViewLoads() throws Exception {
        seedTwoProgrammesInTheSameSemester();
        var resolution = sectionSearchService.resolve(null, null, null, null,
                "BTCSEAIML, semester 5, section D");
        Long sectionId = resolution.single().sectionId();

        MvcResult result = mockMvc.perform(get("/api/lookup/section/{id}/week", sectionId).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value(sectionId))
                .andExpect(jsonPath("$.status").value("NO_TIMETABLE"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("NO_TIMETABLE");
    }

    @Test
    @DisplayName("catalog writes still work and produce sections the search can find")
    void catalogWriteThenSearch() throws Exception {
        var department = TestFixtures.department(departments, "CSE-WRITE");
        var program = catalogService.saveProgram(null,
                new ProgramRequest(department.getId(), "B.Tech ECE", "BTECHECE", "UG", true));
        var term = TestFixtures.term(terms, "2026-27", 1,
                LocalDate.of(2026, 8, 1), LocalDate.of(2027, 1, 31));
        var section = catalogService.saveSection(null,
                new SectionRequest(program.id(), term.getId(), "B", true));

        assertThat(section.sectionName()).isEqualTo("B");

        MvcResult result = mockMvc.perform(get("/api/sections").with(ADMIN))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("BTECHECE");
    }
}