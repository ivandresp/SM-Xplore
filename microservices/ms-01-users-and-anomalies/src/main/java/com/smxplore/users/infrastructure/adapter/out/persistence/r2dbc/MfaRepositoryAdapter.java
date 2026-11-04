package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc;

import com.smxplore.users.application.port.out.MfaRepository;
import com.smxplore.users.domain.model.MfaEnrollment;
import com.smxplore.users.infrastructure.security.MfaSecretCipher;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class MfaRepositoryAdapter implements MfaRepository {

    private static final String SELECT_BY_USER = """
            SELECT user_id, secret, enabled, last_used_step, created_at, confirmed_at
            FROM mfa_enrollments
            WHERE user_id = :userId
            """;

    private static final String UPSERT = """
            INSERT INTO mfa_enrollments (user_id, secret, enabled, last_used_step, created_at, confirmed_at)
            VALUES (:userId, :secret, :enabled, :lastUsedStep, :createdAt, :confirmedAt)
            ON CONFLICT (user_id) DO UPDATE SET
                secret = EXCLUDED.secret,
                enabled = EXCLUDED.enabled,
                last_used_step = EXCLUDED.last_used_step,
                confirmed_at = EXCLUDED.confirmed_at
            """;

    private static final String CLAIM_STEP = """
            UPDATE mfa_enrollments
            SET last_used_step = :step
            WHERE user_id = :userId
              AND (last_used_step IS NULL OR last_used_step < :step)
            """;

    private static final String DELETE_CODES = "DELETE FROM mfa_recovery_codes WHERE user_id = :userId";

    private static final String INSERT_CODE = """
            INSERT INTO mfa_recovery_codes (id, user_id, code_hash, created_at, used_at)
            VALUES (:id, :userId, :codeHash, :createdAt, NULL)
            """;

    private static final String CONSUME_CODE = """
            UPDATE mfa_recovery_codes
            SET used_at = :now
            WHERE user_id = :userId AND code_hash = :codeHash AND used_at IS NULL
            """;

    private static final String DELETE_ENROLLMENT = "DELETE FROM mfa_enrollments WHERE user_id = :userId";

    private final DatabaseClient databaseClient;
    private final MfaSecretCipher secretCipher;

    public MfaRepositoryAdapter(DatabaseClient databaseClient, MfaSecretCipher secretCipher) {
        this.databaseClient = databaseClient;
        this.secretCipher = secretCipher;
    }

    @Override
    public Mono<MfaEnrollment> findByUserId(UUID userId) {
        return databaseClient
                .sql(SELECT_BY_USER)
                .bind("userId", userId)
                .map((row, metadata) -> {
                    String stored = row.get("secret", String.class);
                    MfaEnrollment enrollment = new MfaEnrollment(
                            row.get("user_id", UUID.class),
                            secretCipher.decrypt(stored),
                            Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
                            row.get("last_used_step", Long.class),
                            toInstant(row.get("created_at", OffsetDateTime.class)),
                            toInstant(row.get("confirmed_at", OffsetDateTime.class)));
                    return new Loaded(enrollment, !MfaSecretCipher.isEncrypted(stored));
                })
                .one()
                .flatMap(loaded -> loaded.legacy()
                        ? save(loaded.enrollment()).thenReturn(loaded.enrollment())
                        : Mono.just(loaded.enrollment()));
    }

    @Override
    public Mono<Void> save(MfaEnrollment enrollment) {
        GenericExecuteSpec spec = databaseClient
                .sql(UPSERT)
                .bind("userId", enrollment.userId())
                .bind("secret", secretCipher.encrypt(enrollment.secret()))
                .bind("enabled", enrollment.enabled())
                .bind("createdAt", toOffsetDateTime(enrollment.createdAt()));
        spec = bindNullable(spec, "lastUsedStep", enrollment.lastUsedStep(), Long.class);
        spec = bindNullable(spec, "confirmedAt", toOffsetDateTime(enrollment.confirmedAt()), OffsetDateTime.class);
        return spec.fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Void> deleteByUserId(UUID userId) {
        return databaseClient
                .sql(DELETE_CODES)
                .bind("userId", userId)
                .fetch()
                .rowsUpdated()
                .then(databaseClient
                        .sql(DELETE_ENROLLMENT)
                        .bind("userId", userId)
                        .fetch()
                        .rowsUpdated())
                .then();
    }

    @Override
    public Mono<Boolean> claimStep(UUID userId, long step) {
        return databaseClient
                .sql(CLAIM_STEP)
                .bind("userId", userId)
                .bind("step", step)
                .fetch()
                .rowsUpdated()
                .map(updated -> updated > 0);
    }

    @Override
    public Mono<Void> replaceRecoveryCodes(UUID userId, List<String> codeHashes, Instant now) {
        return databaseClient
                .sql(DELETE_CODES)
                .bind("userId", userId)
                .fetch()
                .rowsUpdated()
                .thenMany(Flux.fromIterable(codeHashes)
                        .concatMap(codeHash -> databaseClient
                                .sql(INSERT_CODE)
                                .bind("id", UUID.randomUUID())
                                .bind("userId", userId)
                                .bind("codeHash", codeHash)
                                .bind("createdAt", toOffsetDateTime(now))
                                .fetch()
                                .rowsUpdated()))
                .then();
    }

    @Override
    public Mono<Boolean> consumeRecoveryCode(UUID userId, String codeHash, Instant now) {
        return databaseClient
                .sql(CONSUME_CODE)
                .bind("userId", userId)
                .bind("codeHash", codeHash)
                .bind("now", toOffsetDateTime(now))
                .fetch()
                .rowsUpdated()
                .map(updated -> updated > 0);
    }

    @Override
    public Mono<Long> deleteUsedRecoveryCodesBefore(Instant cutoff) {
        return databaseClient
                .sql("DELETE FROM mfa_recovery_codes WHERE used_at IS NOT NULL AND used_at < :cutoff")
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

    /**
     * Envoltorio para saber si el secreto leído venía en texto plano y debe re-cifrarse.
     */
    private record Loaded(MfaEnrollment enrollment, boolean legacy) {}
}
