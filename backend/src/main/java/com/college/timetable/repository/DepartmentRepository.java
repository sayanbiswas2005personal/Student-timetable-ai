package com.college.timetable.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.college.timetable.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByCodeIgnoreCase(String code);

    List<Department> findByActiveTrueOrderByNameAsc();
}