package com.college.timetable.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.college.timetable.entity.AcademicTerm;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Long> {

    Optional<AcademicTerm> findByAcademicYearAndSemesterNumber(String academicYear, int semesterNumber);

    List<AcademicTerm> findByActiveTrueOrderByAcademicYearDescSemesterNumberDesc();

    /** Terms whose date range contains {@code date}, most recent first. */
    @Query("""
            select t from AcademicTerm t
            where t.startDate <= :date and t.endDate >= :date
            order by t.academicYear desc, t.semesterNumber desc
            """)
    List<AcademicTerm> findCoveringDate(@Param("date") LocalDate date);

    List<AcademicTerm> findBySemesterNumber(int semesterNumber);
}