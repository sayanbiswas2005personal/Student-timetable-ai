package com.college.timetable.service.admin;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.college.timetable.dto.catalog.AcademicTermDto;
import com.college.timetable.dto.catalog.AcademicTermRequest;
import com.college.timetable.dto.catalog.DepartmentDto;
import com.college.timetable.dto.catalog.DepartmentRequest;
import com.college.timetable.dto.catalog.FacultyDto;
import com.college.timetable.dto.catalog.FacultyRequest;
import com.college.timetable.dto.catalog.ProgramDto;
import com.college.timetable.dto.catalog.ProgramRequest;
import com.college.timetable.dto.catalog.RoomDto;
import com.college.timetable.dto.catalog.RoomRequest;
import com.college.timetable.dto.catalog.SectionDto;
import com.college.timetable.dto.catalog.SectionRequest;
import com.college.timetable.dto.catalog.SubjectDto;
import com.college.timetable.dto.catalog.SubjectRequest;
import com.college.timetable.entity.AcademicTerm;
import com.college.timetable.entity.Department;
import com.college.timetable.entity.Faculty;
import com.college.timetable.entity.Program;
import com.college.timetable.entity.Room;
import com.college.timetable.entity.Section;
import com.college.timetable.entity.Subject;
import com.college.timetable.exception.ApiException;
import com.college.timetable.repository.AcademicTermRepository;
import com.college.timetable.repository.DepartmentRepository;
import com.college.timetable.repository.FacultyRepository;
import com.college.timetable.repository.ProgramRepository;
import com.college.timetable.repository.RoomRepository;
import com.college.timetable.repository.SectionRepository;
import com.college.timetable.repository.SubjectRepository;
import com.college.timetable.security.CurrentUser;
import com.college.timetable.service.audit.AuditService;

/**
 * Management of the reference data the timetable depends on: departments, programmes, academic
 * terms, sections, subjects, faculty and rooms.
 *
 * <p>Lookups only ever read from these tables, so this class is the gate that decides which
 * courses and sections a search is allowed to resolve to.
 */
@Service
public class CatalogService {

    private final DepartmentRepository departmentRepository;
    private final ProgramRepository programRepository;
    private final AcademicTermRepository termRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final FacultyRepository facultyRepository;
    private final RoomRepository roomRepository;
    private final AuditService auditService;

    public CatalogService(DepartmentRepository departmentRepository,
                          ProgramRepository programRepository,
                          AcademicTermRepository termRepository,
                          SectionRepository sectionRepository,
                          SubjectRepository subjectRepository,
                          FacultyRepository facultyRepository,
                          RoomRepository roomRepository,
                          AuditService auditService) {
        this.departmentRepository = departmentRepository;
        this.programRepository = programRepository;
        this.termRepository = termRepository;
        this.sectionRepository = sectionRepository;
        this.subjectRepository = subjectRepository;
        this.facultyRepository = facultyRepository;
        this.roomRepository = roomRepository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public List<DepartmentDto> departments() {
        return departmentRepository.findByActiveTrueOrderByNameAsc().stream().map(DepartmentDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ProgramDto> programs(Long departmentId) {
        var programs = departmentId == null
                ? programRepository.findByActiveTrueOrderByNameAsc()
                : programRepository.findByDepartmentIdAndActiveTrueOrderByNameAsc(departmentId);
        return programs.stream().map(ProgramDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AcademicTermDto> terms() {
        return termRepository.findByActiveTrueOrderByAcademicYearDescSemesterNumberDesc()
                .stream().map(AcademicTermDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SectionDto> sections(Long programId, Long termId) {
        var sections = programId != null
                ? sectionRepository.findActiveByProgram(programId)
                : termId != null
                        ? sectionRepository.findActiveByTerm(termId)
                        : sectionRepository.findByActiveTrueOrderByProgramNameAscSectionNameAsc();
        return sections.stream().map(SectionDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SubjectDto> subjects() {
        return subjectRepository.findByActiveTrueOrderBySubjectNameAsc().stream().map(SubjectDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<FacultyDto> faculties() {
        return facultyRepository.findByActiveTrueOrderByFacultyNameAsc().stream().map(FacultyDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RoomDto> rooms() {
        return roomRepository.findAllByOrderByRoomCodeAsc().stream().map(RoomDto::from).toList();
    }

    // --------------------------------------------------------------- write

    @Transactional
    public DepartmentDto saveDepartment(Long id, DepartmentRequest request) {
        Department department = id == null ? new Department() : departmentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Department " + id + " was not found."));
        String code = request.code().trim().toUpperCase(java.util.Locale.ROOT);
        departmentRepository.findByCodeIgnoreCase(code)
                .filter(existing -> id == null || !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw ApiException.conflict("Department code " + code + " is already in use.");
                });
        department.setName(request.name().trim());
        department.setCode(code);
        department.setActive(request.active() == null || request.active());
        DepartmentDto dto = DepartmentDto.from(departmentRepository.save(department));
        audit(id == null ? "CATALOG_DEPARTMENT_CREATE" : "CATALOG_DEPARTMENT_UPDATE", "Department",
                dto.id(), code);
        return dto;
    }

    @Transactional
    public ProgramDto saveProgram(Long id, ProgramRequest request) {
        Program program = id == null ? new Program() : programRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Programme " + id + " was not found."));
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> ApiException.badRequest("DEPARTMENT_NOT_FOUND",
                        "Department " + request.departmentId() + " does not exist."));
        String code = request.code().trim().toUpperCase(java.util.Locale.ROOT);
        programRepository.findByCodeIgnoreCase(code)
                .filter(existing -> id == null || !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw ApiException.conflict("Programme code " + code + " is already in use.");
                });
        program.setDepartment(department);
        program.setName(request.name().trim());
        program.setCode(code);
        program.setDegreeType(blankToNull(request.degreeType()));
        program.setActive(request.active() == null || request.active());
        ProgramDto dto = ProgramDto.from(programRepository.save(program));
        audit(id == null ? "CATALOG_PROGRAM_CREATE" : "CATALOG_PROGRAM_UPDATE", "Program", dto.id(), code);
        return dto;
    }

    @Transactional
    public AcademicTermDto saveTerm(Long id, AcademicTermRequest request) {
        AcademicTerm term = id == null ? new AcademicTerm() : termRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Academic term " + id + " was not found."));
        if (request.endDate().isBefore(request.startDate())) {
            throw ApiException.badRequest("INVALID_EFFECTIVE_DATES",
                    "The end date must not be earlier than the start date.");
        }
        termRepository.findByAcademicYearAndSemesterNumber(request.academicYear().trim(), request.semesterNumber())
                .filter(existing -> id == null || !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw ApiException.conflict("That academic year and semester already exist.");
                });
        term.setAcademicYear(request.academicYear().trim());
        term.setSemesterNumber(request.semesterNumber());
        term.setStartDate(request.startDate());
        term.setEndDate(request.endDate());
        term.setActive(request.active() == null || request.active());
        AcademicTermDto dto = AcademicTermDto.from(termRepository.save(term));
        audit(id == null ? "CATALOG_TERM_CREATE" : "CATALOG_TERM_UPDATE", "AcademicTerm", dto.id(),
                request.academicYear() + " sem " + request.semesterNumber());
        return dto;
    }

    @Transactional
    public SectionDto saveSection(Long id, SectionRequest request) {
        Section section = id == null ? new Section() : sectionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Section " + id + " was not found."));
        Program program = programRepository.findById(request.programId())
                .orElseThrow(() -> ApiException.badRequest("PROGRAM_NOT_FOUND",
                        "Programme " + request.programId() + " does not exist."));
        AcademicTerm term = termRepository.findById(request.academicTermId())
                .orElseThrow(() -> ApiException.badRequest("TERM_NOT_FOUND",
                        "Academic term " + request.academicTermId() + " does not exist."));
        String name = request.sectionName().trim().toUpperCase(java.util.Locale.ROOT);
        sectionRepository.findByProgramIdAndAcademicTermIdAndSectionNameIgnoreCase(program.getId(),
                        term.getId(), name)
                .filter(existing -> id == null || !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw ApiException.conflict("That section already exists for this programme and term.");
                });
        section.setProgram(program);
        section.setAcademicTerm(term);
        section.setSectionName(name);
        section.setActive(request.active() == null || request.active());
        SectionDto dto = SectionDto.from(sectionRepository.save(section));
        audit(id == null ? "CATALOG_SECTION_CREATE" : "CATALOG_SECTION_UPDATE", "Section", dto.id(),
                program.getCode() + " term " + term.getId() + " section " + name);
        return dto;
    }

    @Transactional
    public SubjectDto saveSubject(Long id, SubjectRequest request) {
        Subject subject = id == null ? new Subject() : subjectRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Subject " + id + " was not found."));
        String code = request.subjectCode().trim().toUpperCase(java.util.Locale.ROOT);
        subjectRepository.findBySubjectCodeIgnoreCase(code)
                .filter(existing -> id == null || !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw ApiException.conflict("Subject code " + code + " is already in use.");
                });
        Program program = null;
        if (request.programId() != null) {
            program = programRepository.findById(request.programId())
                    .orElseThrow(() -> ApiException.badRequest("PROGRAM_NOT_FOUND",
                            "Programme " + request.programId() + " does not exist."));
        }
        subject.setSubjectCode(code);
        subject.setSubjectName(request.subjectName().trim());
        subject.setProgram(program);
        subject.setActive(request.active() == null || request.active());
        SubjectDto dto = SubjectDto.from(subjectRepository.save(subject));
        audit(id == null ? "CATALOG_SUBJECT_CREATE" : "CATALOG_SUBJECT_UPDATE", "Subject", dto.id(), code);
        return dto;
    }

    @Transactional
    public FacultyDto saveFaculty(Long id, FacultyRequest request) {
        Faculty faculty = id == null ? new Faculty() : facultyRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Faculty " + id + " was not found."));
        String code = blankToNull(request.facultyCode());
        if (code != null) {
            facultyRepository.findByFacultyCode(code)
                    .filter(existing -> id == null || !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw ApiException.conflict("Faculty code " + code + " is already in use.");
                    });
        }
        faculty.setFacultyName(request.facultyName().trim());
        faculty.setFacultyCode(code);
        faculty.setActive(request.active() == null || request.active());
        FacultyDto dto = FacultyDto.from(facultyRepository.save(faculty));
        audit(id == null ? "CATALOG_FACULTY_CREATE" : "CATALOG_FACULTY_UPDATE", "Faculty", dto.id(),
                request.facultyName());
        return dto;
    }

    @Transactional
    public RoomDto saveRoom(Long id, RoomRequest request) {
        Room room = id == null ? new Room() : roomRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Room " + id + " was not found."));
        String code = request.roomCode().trim().toUpperCase(java.util.Locale.ROOT);
        roomRepository.findByRoomCodeIgnoreCase(code)
                .filter(existing -> id == null || !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw ApiException.conflict("Room code " + code + " is already in use.");
                });
        room.setRoomCode(code);
        room.setBuilding(blankToNull(request.building()));
        room.setFloor(blankToNull(request.floor()));
        RoomDto dto = RoomDto.from(roomRepository.save(room));
        audit(id == null ? "CATALOG_ROOM_CREATE" : "CATALOG_ROOM_UPDATE", "Room", dto.id(), code);
        return dto;
    }

    private void audit(String action, String entityType, Long entityId, String detail) {
        auditService.recordAs(CurrentUser.id(), CurrentUser.username(), action, entityType, entityId, detail);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Local date helper used by callers that build effective ranges. */
    public static LocalDate today(java.time.Clock clock, java.time.ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }
}