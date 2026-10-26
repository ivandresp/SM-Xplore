package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.PasswordResetToken;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface PasswordResetTokenRepository {

    Mono<PasswordResetToken> save(PasswordResetToken token);

    Mono<PasswordResetToken> findByTokenHash(String tokenHash);

    Mono<Void> invalidateAllForUser(UUID userId, Instant now);
}
