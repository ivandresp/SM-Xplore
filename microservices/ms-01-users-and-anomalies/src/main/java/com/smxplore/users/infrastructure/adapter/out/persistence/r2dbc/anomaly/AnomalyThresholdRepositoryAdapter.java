package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc.anomaly;

import com.smxplore.users.application.port.out.AnomalyThresholdRepository;
import com.smxplore.users.domain.model.AnomalySeverity;
import com.smxplore.users.domain.model.AnomalyThreshold;
import com.smxplore.users.domain.model.AnomalyType;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class AnomalyThresholdRepositoryAdapter implements AnomalyThresholdRepository {

    private static final String SELECT = """
            SELECT type, threshold_value, window_seconds, severity, enabled, updated_at, updated_by
            FROM anomaly_thresholds
            """;

    private static final String UPSERT = """
            INSERT INTO anomaly_thresholds (type, threshold_value, window_seconds, severity, enabled, updated_at,
                                            updated_by)
            VALUES (:type, :thresholdValue, :windowSeconds, :severity, :enabled, :updatedAt, :updatedBy)
            ON CONFLICT (type) DO UPDATE SET
                threshold_value = EXCLUDED.threshold_value,
                window_seconds = EXCLUDED.window_seconds,
                severity = EXCLUDED.severity,
                enabled = EXCLUDED.enabled,
                updated_at = EXCLUDED.updated_at,
                updated_by = EXCLUDED.updated_by
            """;

    private final DatabaseClient databaseClient;

    public AnomalyThresholdRepositoryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<AnomalyThreshold> findAll() {
        return databaseClient
                .sql(SELECT + " ORDER BY type")
                .map((row, metadata) -> toDomain(row))
                .all();
    }

    @Override
    public Mono<AnomalyThreshold> findByType(AnomalyType type) {
        return databaseClient
                .sql(SELECT + " WHERE type = :type")
                .bind("type", type.code())
                .map((row, metadata) -> toDomain(row))
                .one();
    }

    @Override
    public Mono<AnomalyThreshold> save(AnomalyThreshold threshold) {
        GenericExecuteSpec spec = databaseClient
                .sql(UPSERT)
                .bind("type", threshold.type().code())
                .bind("thresholdValue", threshold.thresholdValue())
                .bind("windowSeconds", threshold.windowSeconds())
                .bind("severity", threshold.severity().code())
                .bind("enabled", threshold.enabled())
                .bind("updatedAt", OffsetDateTime.ofInstant(threshold.updatedAt(), ZoneOffset.UTC));
        spec = bindNullable(spec, "updatedBy", threshold.updatedBy(), UUID.class);
        return spec.fetch().rowsUpdated().thenReturn(threshold);
    }

    private static AnomalyThreshold toDomain(Row row) {
        return new AnomalyThreshold(
                AnomalyType.fromCode(row.get("type", String.class)),
                valueOrDefault(row.get("threshold_value", Integer.class)),
                valueOrDefault(row.get("window_seconds", Integer.class)),
                AnomalySeverity.fromCode(row.get("severity", String.class)),
                Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
                toInstant(row.get("updated_at", OffsetDateTime.class)),
                row.get("updated_by", UUID.class));
    }

    private static int valueOrDefault(Integer value) {
        return value == null ? 0 : value;
    }

    private static Instant toInstant(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }

    private static GenericExecuteSpec bindNullable(GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }
}
