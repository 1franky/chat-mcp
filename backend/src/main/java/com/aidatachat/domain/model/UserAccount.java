package com.aidatachat.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String email,
        String normalizedEmail,
        String displayName,
        String passwordHash,
        UserRole role,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public UserAccount {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(email, "email is required");
        Objects.requireNonNull(normalizedEmail, "normalizedEmail is required");
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(passwordHash, "passwordHash is required");
        Objects.requireNonNull(role, "role is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static UserAccount create(
            String email,
            String normalizedEmail,
            String displayName,
            String passwordHash,
            UserRole role,
            Instant now) {
        return new UserAccount(
                UUID.randomUUID(),
                email,
                normalizedEmail,
                displayName,
                passwordHash,
                role,
                true,
                0,
                now,
                now);
    }

    public UserAccount withRole(UserRole newRole, Instant now) {
        return new UserAccount(
                id,
                email,
                normalizedEmail,
                displayName,
                passwordHash,
                newRole,
                active,
                version,
                createdAt,
                now);
    }

    public UserAccount deactivate(Instant now) {
        return new UserAccount(
                id,
                email,
                normalizedEmail,
                displayName,
                passwordHash,
                role,
                false,
                version,
                createdAt,
                now);
    }
}
