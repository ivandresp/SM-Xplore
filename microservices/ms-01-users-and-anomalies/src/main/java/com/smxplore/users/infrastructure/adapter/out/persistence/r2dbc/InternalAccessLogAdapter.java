package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc;

import com.smxplore.users.application.port.out.InternalAccessLog;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/** Adaptador R2DBC del registro de accesos internos (MS01-11). */
@Repository
public class InternalAccessLogAdapter implements InternalAccessLog {

    private static final String INSERT_LOG = """
            INSERT INTO internal_access_log (service, target_user_id, outcome, occurred_at)
            VALUES (:service, :targetUserId, :outcome, :occurredAt)
            """;

    private final DatabaseClient databaseClient;

    public InternalAccessLogAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> record(String service, UUID targetUserId, String outcome, Instant occurredAt) {
        return databaseClient
                .sql(INSERT_LOG)
                .bind("service", service)
                .bind("targetUserId", targetUserId == null ? null : targetUserId)
                .bind("outcome", outcome)
                .bind("occurredAt", OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                .then();
    }
}
