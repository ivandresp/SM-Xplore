package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc.anomaly;

import com.smxplore.users.application.dto.AnomalySearchCriteria;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.port.out.AnomalyRepository;
import com.smxplore.users.domain.model.Anomaly;
import com.smxplore.users.domain.model.AnomalyType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class AnomalyRepositoryAdapter implements AnomalyRepository {

    private static final String SELECT = """
            SELECT id, type, severity, status, description, detected_at, resolved_at, resolved_by, resolution_note
            FROM anomalies
            """;

    private static final String UPSERT = """
            INSERT INTO anomalies (id, type, severity, status, description, detected_at, resolved_at, resolved_by,
                                   resolution_note)
            VALUES (:id, :type, :severity, :status, :description, :detectedAt, :resolvedAt, :resolvedBy,
                    :resolutionNote)
            ON CONFLICT (id) DO UPDATE SET
                severity = EXCLUDED.severity,
                status = EXCLUDED.status,
                resolved_at = EXCLUDED.resolved_at,
                resolved_by = EXCLUDED.resolved_by,
                resolution_note = EXCLUDED.resolution_note
            """;

    private static final String FILTERS = """
            WHERE (CAST(:status AS text) IS NULL OR status = CAST(:status AS text))
              AND (CAST(:severity AS text) IS NULL OR severity = CAST(:severity AS text))
              AND (CAST(:type AS text) IS NULL OR type = CAST(:type AS text))
              AND (CAST(:from AS timestamptz) IS NULL OR detected_at >= CAST(:from AS timestamptz))
              AND (CAST(:to AS timestamptz) IS NULL OR detected_at <= CAST(:to AS timestamptz))
            """;

    private static final String ORDER = """
            ORDER BY CASE WHEN status = 'RESUELTA' THEN 1 ELSE 0 END,
                     CASE severity
                         WHEN 'CRITICA' THEN 0
                         WHEN 'ALTA' THEN 1
                         WHEN 'MEDIA' THEN 2
                         ELSE 3
                     END,
                     detected_at DESC
            """;

    private final DatabaseClient databaseClient;
    private final AnomalyPersistenceMapper mapper = new AnomalyPersistenceMapper();

    public AnomalyRepositoryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Anomaly> save(Anomaly anomaly) {
        GenericExecuteSpec spec = databaseClient
                .sql(UPSERT)
                .bind("id", anomaly.id())
                .bind("type", anomaly.type().code())
                .bind("severity", anomaly.severity().code())
                .bind("status", anomaly.status().code())
                .bind("description", anomaly.description())
                .bind("detectedAt", AnomalyPersistenceMapper.toOffsetDateTime(anomaly.detectedAt()));
        spec = bindNullable(
                spec,
                "resolvedAt",
                AnomalyPersistenceMapper.toOffsetDateTime(anomaly.resolvedAt()),
                OffsetDateTime.class);
        spec = bindNullable(spec, "resolvedBy", anomaly.resolvedBy(), UUID.class);
        spec = bindNullable(spec, "resolutionNote", anomaly.resolutionNote(), String.class);
        return spec.fetch().rowsUpdated().thenReturn(anomaly);
    }

    @Override
    public Mono<Anomaly> findById(UUID anomalyId) {
        return databaseClient
                .sql(SELECT + " WHERE id = :id")
                .bind("id", anomalyId)
                .map((row, metadata) -> mapper.toDomain(mapper.toRecord(row)))
                .one();
    }

    @Override
    public Mono<Anomaly> findLatestByType(AnomalyType type) {
        return databaseClient
                .sql(SELECT + " WHERE type = :type ORDER BY detected_at DESC LIMIT 1")
                .bind("type", type.code())
                .map((row, metadata) -> mapper.toDomain(mapper.toRecord(row)))
                .one();
    }

    @Override
    public Mono<PageResult<Anomaly>> search(AnomalySearchCriteria criteria) {
        Mono<Long> total = bindFilters(
                        databaseClient.sql("SELECT count(*) AS total FROM anomalies " + FILTERS), criteria)
                .map((row, metadata) -> row.get("total", Long.class))
                .one();

        String sql = SELECT + FILTERS + ORDER + " LIMIT :limit OFFSET :offset";
        Mono<List<Anomaly>> content = bindFilters(databaseClient.sql(sql), criteria)
                .bind("limit", criteria.size())
                .bind("offset", (long) criteria.page() * criteria.size())
                .map((row, metadata) -> mapper.toDomain(mapper.toRecord(row)))
                .all()
                .collectList();

        return total.zipWith(content)
                .map(tuple -> PageResult.of(tuple.getT2(), criteria.page(), criteria.size(), tuple.getT1()));
    }

    private static GenericExecuteSpec bindFilters(GenericExecuteSpec spec, AnomalySearchCriteria criteria) {
        spec = bindNullable(
                spec,
                "status",
                criteria.status() == null ? null : criteria.status().code(),
                String.class);
        spec = bindNullable(
                spec,
                "severity",
                criteria.severity() == null ? null : criteria.severity().code(),
                String.class);
        spec = bindNullable(
                spec, "type", criteria.type() == null ? null : criteria.type().code(), String.class);
        spec = bindNullable(spec, "from", toOffsetDateTime(criteria.from()), OffsetDateTime.class);
        return bindNullable(spec, "to", toOffsetDateTime(criteria.to()), OffsetDateTime.class);
    }

    private static GenericExecuteSpec bindNullable(GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return AnomalyPersistenceMapper.toOffsetDateTime(instant);
    }
}
