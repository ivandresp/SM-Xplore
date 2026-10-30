package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc;

import com.smxplore.users.application.port.out.LoginAttemptRepository;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.policy.LoginLockoutPolicy;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class LoginAttemptRepositoryAdapter implements LoginAttemptRepository {

    private static final String SELECT_LOCK = """
            SELECT locked_until
            FROM login_attempts
            WHERE email = :email
            """;

    /**
     * Inserta el primer fallo o incrementa el contador dentro de la ventana vigente. El cálculo se hace en una sola
     * sentencia para que dos intentos concurrentes no pierdan incrementos.
     */
    private static final String RECORD_FAILURE = """
            INSERT INTO login_attempts (email, failed_count, first_failed_at, last_failed_at, locked_until)
            VALUES (:email, 1, :now, :now, NULL)
            ON CONFLICT (email) DO UPDATE SET
                failed_count = CASE
                    WHEN login_attempts.first_failed_at < :windowStart THEN 1
                    ELSE login_attempts.failed_count + 1
                END,
                first_failed_at = CASE
                    WHEN login_attempts.first_failed_at < :windowStart THEN :now
                    ELSE login_attempts.first_failed_at
                END,
                last_failed_at = :now,
                locked_until = CASE
                    WHEN (CASE
                        WHEN login_attempts.first_failed_at < :windowStart THEN 1
                        ELSE login_attempts.failed_count + 1
                    END) >= :maxAttempts THEN :lockedUntil
                    ELSE NULL
                END
            RETURNING locked_until
            """;

    private static final String DELETE_BY_EMAIL = "DELETE FROM login_attempts WHERE email = :email";

    private final DatabaseClient databaseClient;

    public LoginAttemptRepositoryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Duration> lockRemaining(Email email, Instant now) {
        return databaseClient
                .sql(SELECT_LOCK)
                .bind("email", email.value())
                .map((row, metadata) -> remaining(row.get("locked_until", OffsetDateTime.class), now))
                .one()
                .defaultIfEmpty(Duration.ZERO);
    }

    @Override
    public Mono<Duration> recordFailure(Email email, Instant now, LoginLockoutPolicy policy) {
        return databaseClient
                .sql(RECORD_FAILURE)
                .bind("email", email.value())
                .bind("now", toOffsetDateTime(now))
                .bind("windowStart", toOffsetDateTime(now.minus(policy.window())))
                .bind("maxAttempts", policy.maxAttempts())
                .bind("lockedUntil", toOffsetDateTime(now.plus(policy.duration())))
                .map((row, metadata) -> remaining(row.get("locked_until", OffsetDateTime.class), now))
                .one()
                .defaultIfEmpty(Duration.ZERO);
    }

    @Override
    public Mono<Void> clear(Email email) {
        return databaseClient
                .sql(DELETE_BY_EMAIL)
                .bind("email", email.value())
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<Long> deleteLastFailedBefore(Instant cutoff) {
        return databaseClient
                .sql("DELETE FROM login_attempts WHERE last_failed_at < :cutoff")
                .bind("cutoff", toOffsetDateTime(cutoff))
                .fetch()
                .rowsUpdated();
    }

    private static Duration remaining(OffsetDateTime lockedUntil, Instant now) {
        if (lockedUntil == null) {
            return Duration.ZERO;
        }
        Instant until = lockedUntil.toInstant();
        return until.isAfter(now) ? Duration.between(now, until) : Duration.ZERO;
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
