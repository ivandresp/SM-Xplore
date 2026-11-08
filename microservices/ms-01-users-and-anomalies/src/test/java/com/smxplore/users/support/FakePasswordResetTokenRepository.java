package com.smxplore.users.support;

import com.smxplore.users.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.domain.model.PasswordResetToken;
import com.smxplore.users.domain.model.TokenPurpose;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import reactor.core.publisher.Mono;

public final class FakePasswordResetTokenRepository implements PasswordResetTokenRepository {

    private final Map<String, PasswordResetToken> byHash = new ConcurrentHashMap<>();
    private final List<PasswordResetToken> saved = new CopyOnWriteArrayList<>();
    private final List<String> invalidatedPurposes = new CopyOnWriteArrayList<>();

    public FakePasswordResetTokenRepository seed(PasswordResetToken token) {
        byHash.put(token.tokenHash(), token);
        return this;
    }

    public List<PasswordResetToken> saved() {
        return saved;
    }

    public List<String> invalidatedPurposes() {
        return invalidatedPurposes;
    }

    @Override
    public Mono<PasswordResetToken> save(PasswordResetToken token) {
        saved.add(token);
        byHash.put(token.tokenHash(), token);
        return Mono.just(token);
    }

    @Override
    public Mono<PasswordResetToken> findByTokenHash(String tokenHash) {
        return Mono.justOrEmpty(byHash.get(tokenHash));
    }

    @Override
    public Mono<Void> invalidateAllForUser(UUID userId, Instant now) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> invalidateByPurposeForUser(UUID userId, TokenPurpose purpose, Instant now) {
        invalidatedPurposes.add(purpose.name());
        return Mono.empty();
    }

    @Override
    public Mono<Long> deleteCreatedBefore(Instant cutoff) {
        return Mono.just(0L);
    }
}
