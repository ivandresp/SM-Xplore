package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.PasswordHash;
import reactor.core.publisher.Mono;

public interface PasswordHasher {

    Mono<PasswordHash> hash(String rawPassword);

    Mono<Boolean> matches(String rawPassword, PasswordHash passwordHash);
}
