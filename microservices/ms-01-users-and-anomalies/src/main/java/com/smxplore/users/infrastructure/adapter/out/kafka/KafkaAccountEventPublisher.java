package com.smxplore.users.infrastructure.adapter.out.kafka;

import com.smxplore.users.application.port.out.AccountEventPublisher;
import com.smxplore.users.domain.model.Usuario;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

/**
 * Publica users.account.blocked (contrato v1) para que ms-03 despublique las actividades del guia.
 * Es best effort: si Kafka falla se registra y el cambio de estado ya quedo guardado; no hay outbox
 * en ms-01 (deuda anotada en el PR de B3).
 */
@Component
@ConditionalOnProperty(name = "app.events.account-publisher.enabled", havingValue = "true")
public class KafkaAccountEventPublisher implements AccountEventPublisher {

    public static final String TOPIC = "users.account.blocked";
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaAccountEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public KafkaAccountEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> accountBlocked(Usuario target) {
        return Mono.fromRunnable(() -> send(target))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void send(Usuario target) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", target.id().toString());
            payload.put("role", target.role().name());

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.put("eventType", TOPIC);
            envelope.put("eventVersion", 1);
            envelope.put("occurredAt", Instant.now().toString());
            envelope.put("aggregateId", target.id().toString());
            envelope.put("correlationId", UUID.randomUUID().toString());
            envelope.put("producer", "ms-01");
            envelope.put("payload", payload);

            kafka.send(TOPIC, target.id().toString(), objectMapper.writeValueAsString(envelope))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            LOGGER.error("No se pudo publicar {} para {}", TOPIC, target.id(), error);
                        }
                    });
        } catch (RuntimeException exception) {
            LOGGER.error("No se pudo construir {} para {}", TOPIC, target.id(), exception);
        }
    }
}
