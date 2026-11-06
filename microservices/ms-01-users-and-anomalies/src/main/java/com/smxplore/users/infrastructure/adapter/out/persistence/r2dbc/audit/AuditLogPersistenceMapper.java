package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc.audit;

import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.AuditEntry;
import com.smxplore.users.domain.model.Role;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

final class AuditLogPersistenceMapper {

    private static final TypeReference<Map<String, String>> DETAILS_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    AuditLogPersistenceMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AuditEntry toDomain(Row row) {
        return AuditEntry.rehydrate(
                row.get("id", UUID.class),
                row.get("actor_user_id", UUID.class),
                row.get("actor_email", String.class),
                Role.fromCode(row.get("actor_role", String.class)),
                AuditAction.valueOf(row.get("action", String.class)),
                row.get("target_user_id", UUID.class),
                deserializeDetails(row.get("details", String.class)),
                toInstant(row.get("occurred_at", OffsetDateTime.class)));
    }

    String serializeDetails(Map<String, String> details) {
        return details == null || details.isEmpty() ? null : objectMapper.writeValueAsString(details);
    }

    static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private Map<String, String> deserializeDetails(String details) {
        if (details == null || details.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(details, DETAILS_TYPE);
    }
}
