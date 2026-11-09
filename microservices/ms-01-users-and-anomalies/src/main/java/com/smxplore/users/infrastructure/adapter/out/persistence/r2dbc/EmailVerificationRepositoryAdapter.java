package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc;

import com.smxplore.users.application.port.out.EmailVerificationRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class EmailVerificationRepositoryAdapter implements EmailVerificationRepository {

    private static final String IS_VERIFIED = """
            SELECT EXISTS (SELECT 1 FROM email_verifications WHERE user_id = :userId) AS verificado
            """;

    private static final String MARK_VERIFIED = """
            INSERT INTO email_verifications (user_id, verified_at)
            VALUES (:userId, :verifiedAt)
            ON CONFLICT (user_id) DO UPDATE SET verified_at = EXCLUDED.verified_at
            """;

    private final DatabaseClient databaseClient;

    public EmailVerificationRepositoryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Boolean> isVerified(UUID userId) {
        return databaseClient
                .sql(IS_VERIFIED)
                .bind("userId", userId)
                .map((row, metadata) -> Boolean.TRUE.equals(row.get("verificado", Boolean.class)))
                .one()
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Void> markVerified(UUID userId, Instant now) {
        return databaseClient
                .sql(MARK_VERIFIED)
                .bind("userId", userId)
                .bind("verifiedAt", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .fetch()
                .rowsUpdated()
                .then();
    }
}
