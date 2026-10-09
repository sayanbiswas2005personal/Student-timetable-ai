package com.college.timetable.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.college.timetable.entity.ImportJob;
import com.college.timetable.entity.ImportStatus;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {

    List<ImportJob> findByOrderByCreatedAtDesc();

    List<ImportJob> findByStatusOrderByCreatedAtAsc(ImportStatus status);

    Optional<ImportJob> findByStoredFilename(String storedFilename);
}