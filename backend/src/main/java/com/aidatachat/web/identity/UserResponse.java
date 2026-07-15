package com.aidatachat.web.identity;

import com.aidatachat.domain.model.UserAccount;
import com.aidatachat.domain.model.UserRole;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(UserAccount account) {
        return new UserResponse(
                account.id(),
                account.email(),
                account.displayName(),
                account.role(),
                account.active(),
                account.createdAt(),
                account.updatedAt());
    }
}
