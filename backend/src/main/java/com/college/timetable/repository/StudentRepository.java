package com.college.timetable.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.college.timetable.entity.Student;

public interface StudentRepository extends JpaRepository<Student, Long> {

    /**
     * Exact match on the normalized registration number. This is the only lookup used by
     * "who should this student be in right now"; it can never return a different student.
     */
    Optional<Student> findByRegistrationNumberNormalized(String registrationNumberNormalized);

    boolean existsByRegistrationNumberNormalized(String registrationNumberNormalized);

    Optional<Student> findByRegistrationNumberIgnoreCase(String registrationNumber);

    @Query("""
            select s from Student s
            join fetch s.section sec
            join fetch sec.program p
            join fetch sec.academicTerm t
            where s.registrationNumberNormalized = :normalized
            """)
    Optional<Student> findForLookup(@Param("normalized") String normalized);

    Page<Student> findByRegistrationNumberNormalizedContainingIgnoreCase(String fragment, Pageable pageable);

    @Query("""
            select s from Student s
            where s.section.id = :sectionId and s.active = true
            order by s.registrationNumberNormalized asc
            """)
    List<Student> findActiveBySection(@Param("sectionId") Long sectionId);

    long countByActiveTrue();

    @Query("""
            select s from Student s
            join fetch s.section sec
            join fetch sec.program p
            join fetch sec.academicTerm t
            where s.id = :id
            """)
    Optional<Student> findByIdForLookup(@Param("id") Long id);
}