package com.smxplore.users.infrastructure.adapter.out.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smxplore.users.application.port.out.AccountEventPublisher;
import com.smxplore.users.domain.model.Usuario;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.put("eventType", TOPIC);
            envelope.put("eventVersion", 1);
            envelope.put("occurredAt", Instant.now().toString());
            envelope.put("aggregateId", target.id().toString());
            envelope.put("correlationId", UUID.randomUUID().toString());
            envelope.put("producer", "ms-01");
            ObjectNode payload = envelope.putObject("payload");
            payload.put("userId", target.id().toString());
            payload.put("role", target.role().name());

            kafka.send(TOPIC, target.id().toString(), objectMapper.writeValueAsString(envelope))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            LOGGER.error("No se pudo publicar {} para {}", TOPIC, target.id(), error);
                        }
                    });
        } catch (Exception exception) {
            LOGGER.error("No se pudo construir {} para {}", TOPIC, target.id(), exception);
        }
    }
}
