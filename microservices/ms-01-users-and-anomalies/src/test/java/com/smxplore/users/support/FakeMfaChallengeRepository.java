package com.smxplore.users.support;

import com.smxplore.users.application.port.out.MfaChallengeRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

/** Desafíos MFA en memoria para pruebas (MS01-06 / MS01-08). */
public class FakeMfaChallengeRepository implements MfaChallengeRepository {

    private final Map<UUID, Instant> stored = new HashMap<>();

    private final java.util.Set<UUID> consumed = new java.util.HashSet<>();

    @Override
    public Mono<Void> save(UUID jti, UUID userId, Instant expiresAt) {
        stored.put(jti, expiresAt);
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> consume(UUID jti, Instant now) {
        if (!stored.containsKey(jti) || consumed.contains(jti)) {
            return Mono.just(false);
        }
        consumed.add(jti);
        return Mono.just(true);
    }

    @Override
    public Mono<Long> deleteExpiredBefore(Instant cutoff) {
        return Mono.just(0L);
    }

    public boolean isConsumed(UUID jti) {
        return consumed.contains(jti);
    }
}
