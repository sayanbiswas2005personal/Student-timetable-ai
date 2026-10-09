package com.college.timetable.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.college.timetable.entity.AppUser;
import com.college.timetable.entity.AuditLog;
import com.college.timetable.entity.Role;
import com.college.timetable.exception.ApiException;
import com.college.timetable.repository.AppUserRepository;
import com.college.timetable.security.CurrentUser;
import com.college.timetable.service.audit.AuditService;
import com.college.timetable.service.security.InMemoryRateLimiter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Account administration and the audit trail.
 *
 * <p>Accounts can only be created here by an existing administrator, or through the documented CLI
 * bootstrap command. There is deliberately no public sign up endpoint.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Administration")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AppUserRepository userRepository;
    private final AuditService auditService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final InMemoryRateLimiter rateLimiter;

    public AdminController(AppUserRepository userRepository,
                           AuditService auditService,
                           org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                           InMemoryRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/users")
    @Operation(summary = "List accounts")
    public List<UserRow> users() {
        return userRepository.findAll().stream().map(UserRow::from).toList();
    }

    @PostMapping("/users")
    @Operation(summary = "Create an account")
    public ResponseEntity<UserRow> createUser(@Valid @RequestBody CreateUserRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("That username is already taken.");
        }
        validatePassword(request.password());
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(blankToNull(request.displayName()));
        user.setRole(request.role() == null ? Role.STAFF : request.role());
        user.setActive(true);
        user.setPasswordChangedAt(Instant.now());
        AppUser saved = userRepository.save(user);
        auditService.recordAs(CurrentUser.id(), CurrentUser.username(), "USER_CREATE", "AppUser",
                saved.getId(), "Created " + saved.getUsername() + " as " + saved.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserRow.from(saved));
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Activate or deactivate an account")
    public UserRow changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Account " + id + " was not found."));
        if (request.active() && CurrentUser.id() != null && CurrentUser.id().equals(id)) {
            throw ApiException.badRequest("CANNOT_DEACTIVATE_SELF",
                    "You cannot deactivate the account you are signed in with.");
        }
        user.setActive(request.active());
        AppUser saved = userRepository.save(user);
        auditService.recordAs(CurrentUser.id(), CurrentUser.username(),
                request.active() ? "USER_ACTIVATE" : "USER_DEACTIVATE", "AppUser", saved.getId(),
                saved.getUsername());
        return UserRow.from(saved);
    }

    @PostMapping("/users/{id}/reset-password")
    @Operation(summary = "Set a new password for an account")
    public UserRow resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        validatePassword(request.newPassword());
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Account " + id + " was not found."));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        AppUser saved = userRepository.save(user);
        // Force the account to obtain a fresh session.
        rateLimiter.reset("login:" + saved.getUsername());
        auditService.recordAs(CurrentUser.id(), CurrentUser.username(), "USER_PASSWORD_RESET",
                "AppUser", saved.getId(), "Password reset for " + saved.getUsername());
        return UserRow.from(saved);
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Recent administrative and authentication activity")
    public List<AuditRow> auditLogs(@RequestParam(defaultValue = "100") int limit) {
        return auditService.recent(limit).stream().map(AuditRow::from).toList();
    }

    @GetMapping("/audit-logs/{entityType}/{entityId}")
    @Operation(summary = "History of one record")
    public List<AuditRow> auditLogsForEntity(@PathVariable String entityType, @PathVariable Long entityId) {
        return auditService.forEntity(entityType, entityId).stream().map(AuditRow::from).toList();
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 12) {
            throw ApiException.badRequest("WEAK_PASSWORD",
                    "Use a password of at least 12 characters.");
        }
        if (password.chars().distinct().count() < 5) {
            throw ApiException.badRequest("WEAK_PASSWORD",
                    "Use a password with more variety, not the same character repeated.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateUserRequest(@NotBlank @Size(max = 60) String username,
                                    @NotBlank @Size(max = 200) String password,
                                    @Size(max = 120) String displayName,
                                    @NotNull Role role) {
    }

    public record StatusRequest(@NotNull Boolean active) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(max = 200) String newPassword) {
    }

    public record UserRow(Long id, String username, String displayName, String role, boolean active,
                          Instant lastLoginAt, Instant createdAt) {
        static UserRow from(AppUser user) {
            return new UserRow(user.getId(), user.getUsername(), user.getDisplayName(),
                    user.getRole().name(), user.isActive(), user.getLastLoginAt(), user.getCreatedAt());
        }
    }

    public record AuditRow(Long id, String username, String action, String entityType, Long entityId,
                           Instant occurredAt, String details, String ipAddress) {
        static AuditRow from(AuditLog log) {
            return new AuditRow(log.getId(), log.getUsername(), log.getAction(), log.getEntityType(),
                    log.getEntityId(), log.getOccurredAt(), log.getDetails(), log.getIpAddress());
        }
    }
}