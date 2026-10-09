package com.college.timetable.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.college.timetable.entity.Faculty;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {

    Optional<Faculty> findByFacultyCode(String facultyCode);

    Optional<Faculty> findByFacultyNameIgnoreCase(String facultyName);

    List<Faculty> findByActiveTrueOrderByFacultyNameAsc();

    @Query("""
            select f from Faculty f
            where f.active = true
              and (lower(f.facultyName) like :q or lower(coalesce(f.facultyCode, '')) like :q)
            order by f.facultyName asc
            """)
    List<Faculty> search(@Param("q") String lowerCaseLikeQuery);
}