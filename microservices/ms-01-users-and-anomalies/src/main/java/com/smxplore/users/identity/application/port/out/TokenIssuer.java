package com.smxplore.users.identity.application.port.out;

import com.smxplore.users.identity.domain.model.User;

public interface TokenIssuer {

    IssuedToken issue(User user);

    record IssuedToken(String value, java.time.Instant expiresAt) {}
}
