package com.smxplore.activitiesevents.infrastructure.adapters.in.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smxplore.activitiesevents.application.events.RefreshSiteSnapshotUseCase.SiteChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads a {@code catalog.site.changed} message.
 *
 * <p>v1 has no {@code managerId}, so a v1 message is skipped instead of failing: the snapshot keeps
 * its current manager, which is exactly what v1 could tell us.
 *
 * <p>El mensaje reasigna {@code manager_id} de los eventos del sitio, asi que se exige el productor
 * del contrato ({@code ms-04}) y una marca temporal que no venga del futuro: sin Kafka autenticado
 * cualquiera con acceso al topic podria firmar un cambio con {@code updatedAt} lejano y quedarse con
 * la gestion del sitio. Un mensaje atrasado no se descarta aqui; el {@code UPDATE} condicional de
 * {@code EventPostgresAdapter} ya ignora los snapshots mas viejos que el aplicado.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SiteChangedEventDecoder {

    public static final String EVENT_TYPE = "catalog.site.changed";
    /** Unico productor del contrato: ms-04 (sites and services). */
    private static final String EXPECTED_PRODUCER = "ms-04";
    private static final int FIRST_VERSION_WITH_MANAGER = 2;
    /** Tolerancia de reloj entre ms-04 y este servicio antes de considerar la marca manipulada. */
    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);

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
            var producer = envelope.path("producer").asText("");
            if (!EXPECTED_PRODUCER.equals(producer)) {
                log.warn("Ignoring {} from producer '{}': expected '{}'", EVENT_TYPE, producer,
                        EXPECTED_PRODUCER);
                return Optional.empty();
            }
            var version = envelope.path("eventVersion").asInt();
            if (version < FIRST_VERSION_WITH_MANAGER) {
                log.debug("Ignoring {} v{}: it does not carry managerId", EVENT_TYPE, version);
                return Optional.empty();
            }
            var payload = envelope.path("payload");
            var updatedAt = instant(payload, "updatedAt");
            if (updatedAt.isAfter(Instant.now().plus(MAX_FUTURE_SKEW))) {
                log.warn("Ignoring {} v{} for site {}: updatedAt {} is in the future", EVENT_TYPE,
                        version, payload.path("siteId").asText(), updatedAt);
                return Optional.empty();
            }
            return Optional.of(new SiteChanged(
                    uuid(payload, "siteId"),
                    payload.hasNonNull("name") ? payload.get("name").asText() : null,
                    uuid(payload, "managerId"),
                    updatedAt));
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
