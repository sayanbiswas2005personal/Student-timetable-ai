package com.college.timetable.service.audit;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.college.timetable.entity.AuditLog;
import com.college.timetable.repository.AuditLogRepository;

/**
 * Writes the audit trail.
 *
 * <p>Audit rows are persisted in their own transaction so that a failure in the audited operation
 * does not silently discard the record of the attempt.
 *
 * <p>Callers must pass already-safe detail text. Passwords, session identifiers and bulk student
 * data never belong in {@code details}.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, Long entityId, String details) {
        record(action, entityType, entityId, details, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAs(Long userId, String username, String action, String entityType,
                         Long entityId, String details) {
        record(action, entityType, entityId, details, userId, username);
    }

    private void record(String action, String entityType, Long entityId, String details,
                        Long userId, String username) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(truncate(details, 2000));
        log.setOccurredAt(Instant.now());
        log.setUserId(userId);
        log.setUsername(username);
        log.setIpAddress(currentIp());
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> recent(int limit) {
        return repository.findTop200ByOrderByOccurredAtDesc().stream()
                .limit(Math.max(1, Math.min(limit, 200)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> forEntity(String entityType, Long entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(entityType, entityId);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String currentIp() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            var request = servletAttributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }
}