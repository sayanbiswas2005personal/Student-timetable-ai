package com.college.timetable.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.college.timetable.dto.common.PageResponse;
import com.college.timetable.dto.student.CreateStudentRequest;
import com.college.timetable.dto.student.StudentResponse;
import com.college.timetable.dto.student.StudentStatusRequest;
import com.college.timetable.dto.student.UpdateStudentRequest;
import com.college.timetable.service.student.StudentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/students")
@Tag(name = "Students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/by-registration")
    @Operation(summary = "Look up one student by registration number")
    public StudentResponse getByRegistrationNumber(
            @Parameter(example = "UG/02/BTCSEAIML/2023/024") @RequestParam("reg") String registrationNumber) {
        return studentService.getByRegistrationNumber(registrationNumber);
    }

    @GetMapping("/by-registration/{*registrationNumber}")
    @Operation(summary = "Look up one student by registration number (path form)")
    public StudentResponse getByRegistrationNumberPath(
            @Parameter(description = "Registration number; the catch all form exists because college "
                    + "registration numbers contain slashes.")
            @PathVariable String registrationNumber) {
        return studentService.getByRegistrationNumber(registrationNumber.replaceFirst("^/+", ""));
    }

    @GetMapping("/search")
    @Operation(summary = "Partial registration number search for the administration screens")
    public PageResponse<StudentResponse> search(@RequestParam("q") String q,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "25") int size) {
        return PageResponse.of(studentService.search(q, PageRequest.of(Math.max(0, page), clampSize(size))));
    }

    @GetMapping("/section/{sectionId}")
    @Operation(summary = "Students enrolled in a section")
    public List<StudentResponse> bySection(@PathVariable Long sectionId) {
        return studentService.listBySection(sectionId);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a student")
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody CreateStudentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a student")
    public StudentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateStudentRequest request) {
        return studentService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate or deactivate a student")
    public StudentResponse changeStatus(@PathVariable Long id,
                                        @Valid @RequestBody StudentStatusRequest request) {
        return studentService.changeStatus(id, request.active());
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}