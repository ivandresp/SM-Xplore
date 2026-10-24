package com.smxplore.users.identity.infrastructure.adapter.out.security;

import com.smxplore.users.identity.application.port.out.PasswordHasher;
import com.smxplore.users.identity.domain.model.PasswordHash;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PasswordHash hash(String rawPassword) {
        return new PasswordHash(passwordEncoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash.value());
    }
}
