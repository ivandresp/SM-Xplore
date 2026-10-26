package com.smxplore.users.infrastructure.adapter.out.security;

import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.domain.model.PasswordHash;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Mono<PasswordHash> hash(String rawPassword) {
        return Mono.fromCallable(() -> new PasswordHash(passwordEncoder.encode(rawPassword)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> matches(String rawPassword, PasswordHash passwordHash) {
        return Mono.fromCallable(() -> passwordEncoder.matches(rawPassword, passwordHash.value()))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
