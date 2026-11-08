package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc;

import com.smxplore.users.application.port.out.MfaChallengeRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Adaptador R2DBC de {@code mfa_challenges}.
 *
 * <p>MS01-06: el consumo es atómico ({@code UPDATE ... WHERE jti = :jti AND used_at IS NULL}), así que dos peticiones
 * simultáneas con el mismo desafío solo pueden canjearlo una vez.
 */
@Repository
public class MfaChallengeRepositoryAdapter implements MfaChallengeRepository {

    private static final String INSERT = """
            INSERT INTO mfa_challenges (jti, user_id, expires_at)
            VALUES (:jti, :userId, :expiresAt)
            ON CONFLICT (jti) DO NOTHING
            """;

    private static final String CONSUME = """
            UPDATE mfa_challenges
            SET used_at = :now
            WHERE jti = :jti AND used_at IS NULL
            RETURNING jti
            """;

    private static final String DELETE_EXPIRED = """
            DELETE FROM mfa_challenges
            WHERE expires_at < :cutoff
            """;

    private final DatabaseClient databaseClient;

    public MfaChallengeRepositoryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> save(UUID jti, UUID userId, Instant expiresAt) {
        return databaseClient
                .sql(INSERT)
                .bind("jti", jti)
                .bind("userId", userId)
                .bind("expiresAt", OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
                .then();
    }

    @Override
    public Mono<Boolean> consume(UUID jti, Instant now) {
        return databaseClient
                .sql(CONSUME)
                .bind("jti", jti)
                .bind("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .map((row, metadata) -> row.get("jti", UUID.class))
                .one()
                .map(ignored -> true)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Long> deleteExpiredBefore(Instant cutoff) {
        return databaseClient
                .sql(DELETE_EXPIRED)
                .bind("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
                .fetch()
                .rowsUpdated();
    }
}
