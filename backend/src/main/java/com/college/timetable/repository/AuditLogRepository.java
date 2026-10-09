package com.college.timetable.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.college.timetable.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByOrderByOccurredAtDesc(Pageable pageable);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, Long entityId);

    List<AuditLog> findTop200ByOrderByOccurredAtDesc();

    void deleteByOccurredAtBefore(Instant cutoff);
}