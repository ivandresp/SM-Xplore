package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa.audit;

import com.smxplore.users.identity.domain.model.AuditEntry;
import com.smxplore.users.identity.domain.model.UserId;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

final class AuditLogPersistenceMapper {

    private static final TypeReference<Map<String, String>> DETAILS_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    AuditLogPersistenceMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AuditLogJpaEntity toEntity(AuditEntry entry) {
        return new AuditLogJpaEntity(
                entry.id(),
                entry.actorId().value(),
                entry.actorEmail(),
                entry.actorRole(),
                entry.action(),
                entry.targetUserId() == null ? null : entry.targetUserId().value(),
                serializeDetails(entry.details()),
                entry.occurredAt());
    }

    AuditEntry toDomain(AuditLogJpaEntity entity) {
        return AuditEntry.rehydrate(
                entity.getId(),
                new UserId(entity.getActorUserId()),
                entity.getActorEmail(),
                entity.getActorRole(),
                entity.getAction(),
                entity.getTargetUserId() == null ? null : new UserId(entity.getTargetUserId()),
                deserializeDetails(entity.getDetails()),
                entity.getOccurredAt());
    }

    private String serializeDetails(Map<String, String> details) {
        return details.isEmpty() ? null : objectMapper.writeValueAsString(details);
    }

    private Map<String, String> deserializeDetails(String details) {
        if (details == null || details.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(details, DETAILS_TYPE);
    }
}
