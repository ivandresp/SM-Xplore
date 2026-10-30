package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc;

import com.smxplore.users.application.port.out.PasswordResetTokenRepository;
import com.smxplore.users.domain.model.PasswordResetToken;
import com.smxplore.users.domain.model.TokenPurpose;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private static final String UPSERT = """
            INSERT INTO password_reset_tokens (id, user_id, token_hash, created_at, expires_at, used_at, purpose)
            VALUES (:id, :userId, :tokenHash, :createdAt, :expiresAt, :usedAt, :purpose)
            ON CONFLICT (id) DO UPDATE SET used_at = EXCLUDED.used_at
            """;

    private static final String SELECT_BY_HASH = """
            SELECT id, user_id, token_hash, created_at, expires_at, used_at, purpose
            FROM password_reset_tokens
            WHERE token_hash = :tokenHash
            """;

    private final DatabaseClient databaseClient;

    public PasswordResetTokenRepositoryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<PasswordResetToken> save(PasswordResetToken token) {
        GenericExecuteSpec spec = databaseClient
                .sql(UPSERT)
                .bind("id", token.id())
                .bind("userId", token.userId())
                .bind("tokenHash", token.tokenHash())
                .bind("createdAt", toOffsetDateTime(token.createdAt()))
                .bind("expiresAt", toOffsetDateTime(token.expiresAt()))
                .bind("purpose", token.purpose().name());
        spec = bindNullable(spec, "usedAt", toOffsetDateTime(token.usedAt()), OffsetDateTime.class);
        return spec.fetch().rowsUpdated().thenReturn(token);
    }

    @Override
    public Mono<PasswordResetToken> findByTokenHash(String tokenHash) {
        return databaseClient
                .sql(SELECT_BY_HASH)
                .bind("tokenHash", tokenHash)
                .map((row, metadata) -> new PasswordResetToken(
                        row.get("id", UUID.class),
                        row.get("user_id", UUID.class),
                        row.get("token_hash", String.class),
                        toInstant(row.get("created_at", OffsetDateTime.class)),
                        toInstant(row.get("expires_at", OffsetDateTime.class)),
                        toInstant(row.get("used_at", OffsetDateTime.class)),
                        TokenPurpose.valueOf(row.get("purpose", String.class))))
                .one();
    }

    @Override
    public Mono<Void> invalidateAllForUser(UUID userId, Instant now) {
        return databaseClient
                .sql("UPDATE password_reset_tokens SET used_at = :now WHERE user_id = :userId AND used_at IS NULL")
                .bind("now", toOffsetDateTime(now))
                .bind("userId", userId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<Long> deleteCreatedBefore(Instant cutoff) {
        return databaseClient
                .sql("DELETE FROM password_reset_tokens WHERE created_at < :cutoff")
                .bind("cutoff", toOffsetDateTime(cutoff))
                .fetch()
                .rowsUpdated();
    }

    private static GenericExecuteSpec bindNullable(GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
