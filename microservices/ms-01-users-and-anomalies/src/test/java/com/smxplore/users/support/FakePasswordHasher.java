package com.smxplore.users.support;

import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.domain.model.PasswordHash;
import reactor.core.publisher.Mono;

public final class FakePasswordHasher implements PasswordHasher {

    private volatile boolean matches = true;

    public FakePasswordHasher matches(boolean value) {
        this.matches = value;
        return this;
    }

    @Override
    public Mono<PasswordHash> hash(String rawPassword) {
        return Mono.just(new PasswordHash("hash:" + rawPassword));
    }

    @Override
    public Mono<Boolean> matches(String rawPassword, PasswordHash passwordHash) {
        return Mono.just(matches);
    }
}
