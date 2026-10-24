package com.smxplore.users.identity.application.port.out;

import com.smxplore.users.identity.domain.model.PasswordHash;

public interface PasswordHasher {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash passwordHash);
}
