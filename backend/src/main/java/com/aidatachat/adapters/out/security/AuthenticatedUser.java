package com.aidatachat.adapters.out.security;

import com.aidatachat.domain.model.UserRole;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class AuthenticatedUser implements UserDetails, CredentialsContainer, Serializable {

    @Serial private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final String normalizedEmail;
    private final String displayName;
    private final UserRole role;
    private final boolean active;
    private transient String passwordHash;

    public AuthenticatedUser(
            UUID id,
            String email,
            String normalizedEmail,
            String displayName,
            String passwordHash,
            UserRole role,
            boolean active) {
        this.id = Objects.requireNonNull(id);
        this.email = Objects.requireNonNull(email);
        this.normalizedEmail = Objects.requireNonNull(normalizedEmail);
        this.displayName = Objects.requireNonNull(displayName);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.role = Objects.requireNonNull(role);
        this.active = active;
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public UserRole role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return normalizedEmail;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
