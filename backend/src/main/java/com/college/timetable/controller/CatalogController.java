package com.college.timetable.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
import com.college.timetable.service.admin.CatalogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Reference data reads available to every signed in user, with writes restricted to ADMIN.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/departments")
    @Operation(summary = "Active departments")
    public List<DepartmentDto> departments() {
        return catalogService.departments();
    }

    @PostMapping("/departments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepartmentDto> createDepartment(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.saveDepartment(null, request));
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DepartmentDto updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentRequest request) {
        return catalogService.saveDepartment(id, request);
    }

    @GetMapping("/programs")
    @Operation(summary = "Active programmes, optionally filtered by department")
    public List<ProgramDto> programs(@RequestParam(required = false) Long departmentId) {
        return catalogService.programs(departmentId);
    }

    @PostMapping("/programs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProgramDto> createProgram(@Valid @RequestBody ProgramRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.saveProgram(null, request));
    }

    @PutMapping("/programs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProgramDto updateProgram(@PathVariable Long id, @Valid @RequestBody ProgramRequest request) {
        return catalogService.saveProgram(id, request);
    }

    @GetMapping("/academic-terms")
    @Operation(summary = "Active academic terms")
    public List<AcademicTermDto> terms() {
        return catalogService.terms();
    }

    @PostMapping("/academic-terms")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AcademicTermDto> createTerm(@Valid @RequestBody AcademicTermRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.saveTerm(null, request));
    }

    @PutMapping("/academic-terms/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AcademicTermDto updateTerm(@PathVariable Long id, @Valid @RequestBody AcademicTermRequest request) {
        return catalogService.saveTerm(id, request);
    }

    @GetMapping("/sections")
    @Operation(summary = "Active sections, optionally filtered by programme or term")
    public List<SectionDto> sections(@RequestParam(required = false) Long programId,
                                     @RequestParam(required = false) Long academicTermId) {
        return catalogService.sections(programId, academicTermId);
    }

    @PostMapping("/sections")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SectionDto> createSection(@Valid @RequestBody SectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.saveSection(null, request));
    }

    @PutMapping("/sections/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SectionDto updateSection(@PathVariable Long id, @Valid @RequestBody SectionRequest request) {
        return catalogService.saveSection(id, request);
    }

    @GetMapping("/subjects")
    public List<SubjectDto> subjects() {
        return catalogService.subjects();
    }

    @PostMapping("/subjects")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubjectDto> createSubject(@Valid @RequestBody SubjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.saveSubject(null, request));
    }

    @PutMapping("/subjects/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SubjectDto updateSubject(@PathVariable Long id, @Valid @RequestBody SubjectRequest request) {
        return catalogService.saveSubject(id, request);
    }

    @GetMapping("/faculties")
    public List<FacultyDto> faculties() {
        return catalogService.faculties();
    }

    @PostMapping("/faculties")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FacultyDto> createFaculty(@Valid @RequestBody FacultyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.saveFaculty(null, request));
    }

    @PutMapping("/faculties/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public FacultyDto updateFaculty(@PathVariable Long id, @Valid @RequestBody FacultyRequest request) {
        return catalogService.saveFaculty(id, request);
    }

    @GetMapping("/rooms")
    public List<RoomDto> rooms() {
        return catalogService.rooms();
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomDto> createRoom(@Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.saveRoom(null, request));
    }

    @PutMapping("/rooms/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomDto updateRoom(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return catalogService.saveRoom(id, request);
    }
}