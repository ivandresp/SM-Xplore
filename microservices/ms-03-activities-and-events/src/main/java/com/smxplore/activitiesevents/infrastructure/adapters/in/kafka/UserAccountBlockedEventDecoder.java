package com.smxplore.activitiesevents.infrastructure.adapters.in.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Lee un {@code users.account.blocked} de ms-01. Solo actua con guias: el bloqueo de una gestora
 * aun no despublica sus eventos (deuda documentada), asi que el resto de roles se ignoran sin
 * romper la particion.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserAccountBlockedEventDecoder {

    public static final String EVENT_TYPE = "users.account.blocked";
    private static final String EXPECTED_PRODUCER = "ms-01";

    private final ObjectMapper objectMapper;

    public Optional<UUID> decode(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        try {
            var envelope = objectMapper.readTree(message);
            if (!EVENT_TYPE.equals(envelope.path("eventType").asText())) {
                log.warn("Ignoring message of type {} on the account topic", envelope.path("eventType").asText());
                return Optional.empty();
            }
            if (!EXPECTED_PRODUCER.equals(envelope.path("producer").asText(""))) {
                log.warn("Ignoring {} from producer '{}'", EVENT_TYPE, envelope.path("producer").asText(""));
                return Optional.empty();
            }
            var payload = envelope.path("payload");
            var role = payload.path("role").asText("");
            if (!"GUIDE".equals(role)) {
                log.debug("Ignoring {} for role {}: only guides unpublish activities yet", EVENT_TYPE, role);
                return Optional.empty();
            }
            var userId = payload.path("userId");
            if (!userId.isTextual() || userId.asText().isBlank()) {
                throw new IllegalArgumentException("userId is required in " + EVENT_TYPE);
            }
            return Optional.of(UUID.fromString(userId.asText()));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(EVENT_TYPE + " is not valid JSON", exception);
        }
    }
}
