package com.college.timetable.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.college.timetable.entity.Role;

/** Convenience access to the signed in principal from services and controllers. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AppUserPrincipal> principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AppUserPrincipal p ? Optional.of(p) : Optional.empty();
    }

    /** Id of the signed in user, or null for anonymous requests. */
    public static Long id() {
        return principal().map(AppUserPrincipal::getId).orElse(null);
    }

    public static String username() {
        return principal().map(AppUserPrincipal::getUsername).orElse(null);
    }

    public static boolean isAdmin() {
        return principal().map(p -> Role.ADMIN.name().equals(p.getRole())).orElse(false);
    }
}