package com.aidatachat.adapters.out.security;

import com.aidatachat.application.port.out.PasswordHashPort;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class Argon2PasswordHashAdapter implements PasswordHashPort {

    private final PasswordEncoder passwordEncoder;

    public Argon2PasswordHashAdapter(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
    }

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
