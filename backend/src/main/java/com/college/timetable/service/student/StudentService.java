package com.college.timetable.service.student;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.college.timetable.dto.student.CreateStudentRequest;
import com.college.timetable.dto.student.StudentResponse;
import com.college.timetable.dto.student.UpdateStudentRequest;
import com.college.timetable.entity.Section;
import com.college.timetable.entity.Student;
import com.college.timetable.exception.ApiException;
import com.college.timetable.repository.SectionRepository;
import com.college.timetable.repository.StudentRepository;
import com.college.timetable.security.CurrentUser;
import com.college.timetable.service.audit.AuditService;
import com.college.timetable.util.RegistrationNumberNormalizer;

/**
 * Administrative management of student records.
 *
 * <p>Registration number uniqueness is enforced on the normalised value, so "ug/02/a/2023/1"
 * and "UG/02/A/2023/1" cannot both exist.
 */
@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final SectionRepository sectionRepository;
    private final AuditService auditService;

    public StudentService(StudentRepository studentRepository,
                          SectionRepository sectionRepository,
                          AuditService auditService) {
        this.studentRepository = studentRepository;
        this.sectionRepository = sectionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public StudentResponse getByRegistrationNumber(String registrationNumber) {
        String normalized = RegistrationNumberNormalizer.normalize(registrationNumber);
        Student student = studentRepository.findForLookup(normalized)
                .orElseThrow(() -> ApiException.notFound("No student with registration number " + normalized));
        return StudentResponse.from(student);
    }

    @Transactional(readOnly = true)
    public StudentResponse getById(Long id) {
        return StudentResponse.from(studentRepository.findByIdForLookup(id)
                .orElseThrow(() -> ApiException.notFound("Student " + id + " was not found.")));
    }

    @Transactional(readOnly = true)
    public Page<StudentResponse> search(String fragment, Pageable pageable) {
        String normalized = RegistrationNumberNormalizer.normalize(fragment);
        if (normalized == null || normalized.isEmpty()) {
            throw ApiException.badRequest("SEARCH_TERM_REQUIRED",
                    "Provide at least part of a registration number to search.");
        }
        return studentRepository.findByRegistrationNumberNormalizedContainingIgnoreCase(normalized, pageable)
                .map(StudentResponse::from);
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> listBySection(Long sectionId) {
        return studentRepository.findActiveBySection(sectionId).stream().map(StudentResponse::from).toList();
    }

    @Transactional
    public StudentResponse create(CreateStudentRequest request) {
        String normalized = RegistrationNumberNormalizer.normalize(request.registrationNumber());
        if (studentRepository.existsByRegistrationNumberNormalized(normalized)) {
            throw ApiException.conflict("A student with registration number " + normalized + " already exists.");
        }
        Section section = requireSection(request.sectionId());

        Student student = new Student();
        student.setRegistrationNumber(request.registrationNumber().trim());
        student.setRegistrationNumberNormalized(normalized);
        student.setFullName(blankToNull(request.fullName()));
        student.setSection(section);
        student.setActive(request.active() == null || request.active());

        StudentResponse response = StudentResponse.from(studentRepository.save(student));
        auditService.recordAs(CurrentUser.id(), CurrentUser.username(), "STUDENT_CREATE", "Student",
                response.id(), "Created " + normalized + " in section " + section.getId());
        return response;
    }

    @Transactional
    public StudentResponse update(Long id, UpdateStudentRequest request) {
        Student student = studentRepository.findByIdForLookup(id)
                .orElseThrow(() -> ApiException.notFound("Student " + id + " was not found."));

        String normalized = RegistrationNumberNormalizer.normalize(request.registrationNumber());
        if (!normalized.equals(student.getRegistrationNumberNormalized())
                && studentRepository.existsByRegistrationNumberNormalized(normalized)) {
            throw ApiException.conflict("A student with registration number " + normalized + " already exists.");
        }
        Section section = requireSection(request.sectionId());

        student.setRegistrationNumber(request.registrationNumber().trim());
        student.setRegistrationNumberNormalized(normalized);
        student.setFullName(blankToNull(request.fullName()));
        student.setSection(section);
        student.setActive(request.active());

        StudentResponse response = StudentResponse.from(studentRepository.save(student));
        auditService.recordAs(CurrentUser.id(), CurrentUser.username(), "STUDENT_UPDATE", "Student",
                response.id(), "Updated " + normalized);
        return response;
    }

    @Transactional
    public StudentResponse changeStatus(Long id, boolean active) {
        Student student = studentRepository.findByIdForLookup(id)
                .orElseThrow(() -> ApiException.notFound("Student " + id + " was not found."));
        student.setActive(active);
        StudentResponse response = StudentResponse.from(studentRepository.save(student));
        auditService.recordAs(CurrentUser.id(), CurrentUser.username(),
                active ? "STUDENT_ACTIVATE" : "STUDENT_DEACTIVATE", "Student", response.id(),
                (active ? "Activated " : "Deactivated ") + student.getRegistrationNumberNormalized());
        return response;
    }

    private Section requireSection(Long sectionId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> ApiException.badRequest("SECTION_NOT_FOUND",
                        "Section " + sectionId + " does not exist. Create the section before enrolling a student."));
        if (!section.isActive()) {
            throw ApiException.badRequest("SECTION_INACTIVE",
                    "Section " + sectionId + " is not active.");
        }
        return section;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}