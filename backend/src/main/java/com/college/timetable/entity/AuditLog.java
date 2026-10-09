package com.college.timetable.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Append only record of administrative and authentication activity.
 *
 * <p>Never store credentials, session identifiers or raw student data here.
 */
@Entity
@Table(name = "audit_logs",
        indexes = {
                @Index(name = "ix_audit_user", columnList = "user_id"),
                @Index(name = "ix_audit_entity", columnList = "entity_type, entity_id"),
                @Index(name = "ix_audit_time", columnList = "occurred_at")
        })
public class AuditLog extends BaseEntity {

    /** Null for actions performed before a successful login (for example failed logins). */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 60)
    private String username;

    /** e.g. STUDENT_UPDATE, TIMETABLE_PUBLISH, LOGIN_SUCCESS, LOGIN_FAILURE. */
    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "details", length = 2000)
    private String details;

    @Column(name = "ip_address", length = 60)
    private String ipAddress;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}