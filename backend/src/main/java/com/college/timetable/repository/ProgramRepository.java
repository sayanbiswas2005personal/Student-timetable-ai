package com.college.timetable.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.college.timetable.entity.Program;

public interface ProgramRepository extends JpaRepository<Program, Long> {

    Optional<Program> findByCodeIgnoreCase(String code);

    List<Program> findByActiveTrueOrderByNameAsc();

    List<Program> findByDepartmentIdAndActiveTrueOrderByNameAsc(Long departmentId);

    /**
     * Case insensitive contains match on name or code, used by the deterministic search parser
     * to resolve a user typed program description against verified values.
     */
    @Query("""
            select p from Program p
            where p.active = true
              and (lower(p.name) like :q or lower(p.code) like :q)
            order by p.name asc
            """)
    List<Program> search(@Param("q") String lowerCaseLikeQuery);
}