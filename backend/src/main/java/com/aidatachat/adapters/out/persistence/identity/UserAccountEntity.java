package com.aidatachat.adapters.out.persistence.identity;

import com.aidatachat.domain.model.UserAccount;
import com.aidatachat.domain.model.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user", schema = "identity")
class UserAccountEntity {

    @Id private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "normalized_email", nullable = false, length = 320, unique = true)
    private String normalizedEmail;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role;

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccountEntity() {}

    UserAccountEntity(UserAccount account) {
        this.id = account.id();
        update(account);
        this.createdAt = account.createdAt();
    }

    void update(UserAccount account) {
        this.email = account.email();
        this.normalizedEmail = account.normalizedEmail();
        this.displayName = account.displayName();
        this.passwordHash = account.passwordHash();
        this.role = account.role();
        this.active = account.active();
        this.updatedAt = account.updatedAt();
    }

    UserAccount toDomain() {
        return new UserAccount(
                id,
                email,
                normalizedEmail,
                displayName,
                passwordHash,
                role,
                active,
                version,
                createdAt,
                updatedAt);
    }
}
