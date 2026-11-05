package com.smxplore.activitiesevents.infrastructure.adapters.in.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smxplore.activitiesevents.application.events.RefreshSiteSnapshotUseCase.SiteChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads a {@code catalog.site.changed} message.
 *
 * <p>v1 has no {@code managerId}, so a v1 message is skipped instead of failing: the snapshot keeps
 * its current manager, which is exactly what v1 could tell us.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SiteChangedEventDecoder {

    static final String EVENT_TYPE = "catalog.site.changed";
    private static final int FIRST_VERSION_WITH_MANAGER = 2;

    private final ObjectMapper objectMapper;

    public Optional<SiteChanged> decode(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        try {
            var envelope = objectMapper.readTree(message);
            if (!EVENT_TYPE.equals(envelope.path("eventType").asText())) {
                log.warn("Ignoring message of type {} on the site topic",
                        envelope.path("eventType").asText());
                return Optional.empty();
            }
            var version = envelope.path("eventVersion").asInt();
            if (version < FIRST_VERSION_WITH_MANAGER) {
                log.debug("Ignoring {} v{}: it does not carry managerId", EVENT_TYPE, version);
                return Optional.empty();
            }
            var payload = envelope.path("payload");
            return Optional.of(new SiteChanged(
                    uuid(payload, "siteId"),
                    payload.hasNonNull("name") ? payload.get("name").asText() : null,
                    uuid(payload, "managerId"),
                    instant(payload, "updatedAt")));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("catalog.site.changed is not valid JSON", exception);
        }
    }

    private static UUID uuid(JsonNode payload, String field) {
        var value = payload.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required in " + EVENT_TYPE);
        }
        return UUID.fromString(value.asText());
    }

    private static Instant instant(JsonNode payload, String field) {
        var value = payload.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required in " + EVENT_TYPE);
        }
        return Instant.parse(value.asText());
    }
}
