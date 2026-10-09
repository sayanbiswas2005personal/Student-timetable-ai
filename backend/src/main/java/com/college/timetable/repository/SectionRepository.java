package com.college.timetable.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.college.timetable.entity.Section;

public interface SectionRepository extends JpaRepository<Section, Long> {

    /**
     * A section name is only unique within a programme and term: "MAIN" exists once per semester,
     * so the term is part of the lookup key.
     */
    Optional<Section> findByProgramIdAndAcademicTermIdAndSectionNameIgnoreCase(Long programId,
                                                                             Long academicTermId,
                                                                             String sectionName);

    @Query("""
            select s from Section s
            where s.active = true
              and s.program.id = :programId
              and s.academicTerm.semesterNumber = :semesterNumber
            order by s.sectionName asc
            """)
    List<Section> findActiveByProgramAndSemester(@Param("programId") Long programId,
                                                 @Param("semesterNumber") int semesterNumber);

    @Query("""
            select s from Section s
            where s.active = true
              and s.program.id = :programId
              and s.academicTerm.id = :termId
            order by s.sectionName asc
            """)
    List<Section> findActiveByProgramAndTerm(@Param("programId") Long programId,
                                             @Param("termId") Long termId);

    @Query("""
            select s from Section s
            where s.active = true
              and lower(s.sectionName) = :sectionName
            order by s.program.name asc
            """)
    List<Section> findActiveBySectionNameIgnoreCase(@Param("sectionName") String sectionName);

    List<Section> findByActiveTrueOrderByProgramNameAscSectionNameAsc();

    @Query("""
            select s from Section s
            where s.active = true and s.academicTerm.id = :termId
            order by s.program.name asc, s.sectionName asc
            """)
    List<Section> findActiveByTerm(@Param("termId") Long termId);

    /** All active sections of one programme, across every term. Small enough to filter in memory. */
    @Query("""
            select s from Section s
            where s.active = true and s.program.id = :programId
            order by s.academicTerm.academicYear desc, s.academicTerm.semesterNumber desc, s.sectionName asc
            """)
    List<Section> findActiveByProgram(@Param("programId") Long programId);
}