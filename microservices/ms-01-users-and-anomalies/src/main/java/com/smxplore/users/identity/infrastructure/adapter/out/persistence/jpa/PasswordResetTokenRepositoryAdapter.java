package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa;

import com.smxplore.users.identity.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.identity.domain.model.PasswordResetToken;
import com.smxplore.users.identity.domain.model.UserId;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository tokens;

    public PasswordResetTokenRepositoryAdapter(PasswordResetTokenJpaRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    @Transactional
    public PasswordResetToken save(PasswordResetToken token) {
        PasswordResetTokenJpaEntity incoming = toEntity(token);
        PasswordResetTokenJpaEntity stored = tokens.findById(token.id()).orElse(null);
        if (stored == null) {
            tokens.save(incoming);
        } else {
            stored.updateFrom(incoming);
            tokens.save(stored);
        }
        return token;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return tokens.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    @Transactional
    public void invalidateAllForUser(UserId userId, Instant now) {
        tokens.invalidateAllForUser(userId.value(), now);
    }

    private PasswordResetTokenJpaEntity toEntity(PasswordResetToken token) {
        return new PasswordResetTokenJpaEntity(
                token.id(),
                token.userId().value(),
                token.tokenHash(),
                token.createdAt(),
                token.expiresAt(),
                token.usedAt());
    }

    private PasswordResetToken toDomain(PasswordResetTokenJpaEntity entity) {
        return new PasswordResetToken(
                entity.getId(),
                new UserId(entity.getUserId()),
                entity.getTokenHash(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                entity.getUsedAt());
    }
}
