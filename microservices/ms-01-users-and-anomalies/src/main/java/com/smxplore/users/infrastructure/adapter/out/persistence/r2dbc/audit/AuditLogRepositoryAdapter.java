package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc.audit;

import com.smxplore.users.application.dto.AuditSearchCriteria;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.port.out.AuditLogRepository;
import com.smxplore.users.domain.model.AuditEntry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@Repository
public class AuditLogRepositoryAdapter implements AuditLogRepository {

    private static final String INSERT = """
            INSERT INTO audit_logs (id, actor_user_id, actor_email, actor_role, action, target_user_id,
                                    details, occurred_at)
            VALUES (:id, :actorUserId, :actorEmail, :actorRole, :action, :targetUserId, :details, :occurredAt)
            """;

    private static final String SELECT = """
            SELECT id, actor_user_id, actor_email, actor_role, action, target_user_id, details, occurred_at
            FROM audit_logs
            """;

    private static final String FILTERS = """
            WHERE (CAST(:actorId AS uuid) IS NULL OR actor_user_id = CAST(:actorId AS uuid))
              AND (CAST(:action AS text) IS NULL OR action = CAST(:action AS text))
              AND (CAST(:targetUserId AS uuid) IS NULL OR target_user_id = CAST(:targetUserId AS uuid))
              AND (CAST(:from AS timestamptz) IS NULL OR occurred_at >= CAST(:from AS timestamptz))
              AND (CAST(:to AS timestamptz) IS NULL OR occurred_at <= CAST(:to AS timestamptz))
            """;

    private final DatabaseClient databaseClient;
    private final AuditLogPersistenceMapper mapper;

    public AuditLogRepositoryAdapter(DatabaseClient databaseClient, ObjectMapper objectMapper) {
        this.databaseClient = databaseClient;
        this.mapper = new AuditLogPersistenceMapper(objectMapper);
    }

    @Override
    public Mono<AuditEntry> save(AuditEntry entry) {
        GenericExecuteSpec spec = databaseClient
                .sql(INSERT)
                .bind("id", entry.id())
                .bind("actorUserId", entry.actorId())
                .bind("actorEmail", entry.actorEmail())
                .bind("actorRole", entry.actorRole().code())
                .bind("action", entry.action().name())
                .bind("occurredAt", toOffsetDateTime(entry.occurredAt()));
        spec = bindNullable(spec, "targetUserId", entry.targetUserId(), UUID.class);
        spec = bindNullable(spec, "details", mapper.serializeDetails(entry.details()), String.class);
        return spec.fetch().rowsUpdated().thenReturn(entry);
    }

    @Override
    public Mono<PageResult<AuditEntry>> search(AuditSearchCriteria criteria) {
        Mono<Long> total = bindFilters(
                        databaseClient.sql("SELECT count(*) AS total FROM audit_logs " + FILTERS), criteria)
                .map((row, metadata) -> row.get("total", Long.class))
                .one();

        String sql = SELECT + FILTERS + " ORDER BY occurred_at DESC LIMIT :limit OFFSET :offset";
        Mono<List<AuditEntry>> content = bindFilters(databaseClient.sql(sql), criteria)
                .bind("limit", criteria.size())
                .bind("offset", (long) criteria.page() * criteria.size())
                .map((row, metadata) -> mapper.toDomain(row))
                .all()
                .collectList();

        return total.zipWith(content)
                .map(tuple -> PageResult.of(tuple.getT2(), criteria.page(), criteria.size(), tuple.getT1()));
    }

    @Override
    public Flux<AuditEntry> export(AuditSearchCriteria criteria, int limit) {
        String sql = SELECT + FILTERS + " ORDER BY occurred_at DESC LIMIT :limit";
        return bindFilters(databaseClient.sql(sql), criteria)
                .bind("limit", limit)
                .map((row, metadata) -> mapper.toDomain(row))
                .all();
    }

    @Override
    public Mono<Long> deleteOccurredBefore(java.time.Instant cutoff) {
        return databaseClient
                .sql("DELETE FROM audit_logs WHERE occurred_at < :cutoff")
                .bind("cutoff", toOffsetDateTime(cutoff))
                .fetch()
                .rowsUpdated();
    }

    private static GenericExecuteSpec bindFilters(GenericExecuteSpec spec, AuditSearchCriteria criteria) {
        spec = bindNullable(spec, "actorId", criteria.actorId(), UUID.class);
        spec = bindNullable(
                spec,
                "action",
                criteria.action() == null ? null : criteria.action().name(),
                String.class);
        spec = bindNullable(spec, "targetUserId", criteria.targetUserId(), UUID.class);
        spec = bindNullable(spec, "from", toOffsetDateTime(criteria.from()), OffsetDateTime.class);
        return bindNullable(spec, "to", toOffsetDateTime(criteria.to()), OffsetDateTime.class);
    }

    private static GenericExecuteSpec bindNullable(GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static OffsetDateTime toOffsetDateTime(java.time.Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
