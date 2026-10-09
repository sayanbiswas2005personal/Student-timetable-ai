package com.college.timetable.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.college.timetable.entity.Subject;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    Optional<Subject> findBySubjectCodeIgnoreCase(String subjectCode);

    List<Subject> findByActiveTrueOrderBySubjectNameAsc();

    @Query("""
            select s from Subject s
            where s.active = true
              and (lower(s.subjectCode) like :q or lower(s.subjectName) like :q)
            order by s.subjectName asc
            """)
    List<Subject> search(@Param("q") String lowerCaseLikeQuery);

    Optional<Subject> findBySubjectCodeIgnoreCaseAndActiveTrue(String subjectCode);
}