package com.smxplore.users.support;

import com.smxplore.users.application.port.out.EmailVerificationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class FakeEmailVerificationRepository implements EmailVerificationRepository {

    private final Set<UUID> verified = new HashSet<>();
    private final List<UUID> markedVerified = new ArrayList<>();

    public FakeEmailVerificationRepository verify(UUID userId) {
        verified.add(userId);
        return this;
    }

    public List<UUID> markedVerified() {
        return markedVerified;
    }

    @Override
    public Mono<Boolean> isVerified(UUID userId) {
        return Mono.just(verified.contains(userId));
    }

    @Override
    public Mono<Void> markVerified(UUID userId, Instant now) {
        verified.add(userId);
        markedVerified.add(userId);
        return Mono.empty();
    }
}
