package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc.anomaly;

import com.smxplore.users.domain.model.Anomaly;
import com.smxplore.users.domain.model.AnomalySeverity;
import com.smxplore.users.domain.model.AnomalyStatus;
import com.smxplore.users.domain.model.AnomalyType;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

final class AnomalyPersistenceMapper {

    AnomalyRecord toRecord(Row row) {
        return new AnomalyRecord(
                row.get("id", UUID.class),
                row.get("type", String.class),
                row.get("severity", String.class),
                row.get("status", String.class),
                row.get("description", String.class),
                row.get("detected_at", OffsetDateTime.class),
                row.get("resolved_at", OffsetDateTime.class),
                row.get("resolved_by", UUID.class),
                row.get("resolution_note", String.class));
    }

    Anomaly toDomain(AnomalyRecord record) {
        return Anomaly.rehydrate(
                record.id(),
                AnomalyType.fromCode(record.type()),
                AnomalySeverity.fromCode(record.severity()),
                AnomalyStatus.fromCode(record.status()),
                record.description(),
                toInstant(record.detectedAt()),
                toInstant(record.resolvedAt()),
                record.resolvedBy(),
                record.resolutionNote());
    }

    static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }
}
