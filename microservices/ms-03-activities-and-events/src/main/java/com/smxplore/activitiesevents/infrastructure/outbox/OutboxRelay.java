package com.smxplore.activitiesevents.infrastructure.outbox;

import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Relays pending outbox rows to Kafka. Disabled unless {@code OUTBOX_ENABLED=true}.
 *
 * <p>El relay apagado es un <b>modo degradado</b>, no el modo normal: los {@code *.changed} se
 * guardan en {@code outbox_events} pero no llegan a Kafka, asi que ms-05 no ve los cambios y la
 * cola crece hasta que la retencion borra las filas terminales. El despliegue (Compose local y de
 * produccion) fija {@code OUTBOX_ENABLED=true}; el valor por defecto se deja apagado para que un
 * {@code java -jar} sin broker no marque eventos como fallidos, y el arranque registra un WARN para
 * que el modo degradado nunca pase inadvertido.
 *
 * <p>Each cycle claims a batch with a lease and a {@code FOR UPDATE SKIP LOCKED} select, so several
 * instances can relay the same table without delivering a row twice and a crashed worker's rows are
 * reclaimed (counting the lost delivery as an attempt) when their lease expires. A failed send is
 * retried with exponential backoff and the row becomes {@code FAILED} after {@code max-attempts};
 * nothing is silently dropped.
 *
 * <p>Delivery is at-least-once: if the row cannot be marked published after a successful send, the
 * event is published again on the next cycle, so consumers must be idempotent (both ms-04 and ms-05
 * are). Ordering is best effort: the batch is processed oldest first, but a retried event can be
 * published after a newer one of the same aggregate, so consumers must tolerate it.
 */
@Component
@Slf4j
public class OutboxRelay {

    private final OutboxPort outbox;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final OutboxProperties properties;
    private final OutboxMetrics metrics;
    private final String workerId = UUID.randomUUID().toString();
    private final AtomicBoolean missingTemplateLogged = new AtomicBoolean();

    public OutboxRelay(OutboxPort outbox,
                       ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
                       OutboxProperties properties,
                       OutboxMetrics metrics) {
        this.outbox = outbox;
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.properties = properties;
        this.metrics = metrics;
        if (!properties.isEnabled()) {
            // Modo degradado visible en el arranque: sin relay los eventos de dominio no salen del
            // servicio y el outbox crece. El despliegue lo enciende con OUTBOX_ENABLED=true.
            log.warn("Outbox relay is disabled (OUTBOX_ENABLED=false): domain events will not reach"
                    + " Kafka and the outbox table will keep growing until the relay is enabled.");
        }
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:2000}")
    public void relay() {
        if (!properties.isEnabled()) {
            return;
        }
        relayOnce()
                .doOnError(error -> log.error("Outbox relay failed", error))
                .onErrorResume(error -> Mono.empty())
                .block(properties.getCycleTimeout());
    }

    Mono<Void> relayOnce() {
        var kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            // Without a KafkaTemplate the events would be claimed and marked failed for nothing:
            // leave them pending until the broker is configured. Se avisa una sola vez: cada ciclo
            // son 2 s y un warn por ciclo llena el log sin aportar nada.
            if (missingTemplateLogged.compareAndSet(false, true)) {
                log.error("Outbox relay is enabled but no KafkaTemplate is available: events stay"
                        + " PENDING until the broker is configured (is spring-boot-starter-kafka"
                        + " on the classpath?)");
            }
            return Mono.empty();
        }
        return outbox.claim(properties.getBatchSize(), properties.getLeaseDuration(),
                        properties.getMaxAttempts(), workerId)
                // UPDATE ... RETURNING no garantiza el orden del CTE: se ordena aquí.
                .sort(Comparator.comparing(claimed -> claimed.event().occurredAt()))
                .concatMap(claimed -> process(claimed, kafkaTemplate))
                .then();
    }

    private Mono<Void> process(OutboxPort.ClaimedEvent claimed, KafkaTemplate<String, String> kafkaTemplate) {
        var event = claimed.event();
        // fromSupplier: un throw síncrono de send() (producer cerrado, SerializationException,
        // buffer lleno) tiene que caer en el onErrorResume de abajo, no escapar del mapper.
        return Mono.fromSupplier(() -> kafkaTemplate.send(
                        event.eventType(), event.aggregateId().toString(), event.payload()))
                .flatMap(Mono::fromFuture)
                .timeout(properties.getSendTimeout())
                // defer: el puerto se invoca solo si el envio funciono, no al construir el Mono.
                .then(Mono.defer(() -> outbox.markPublished(event.id(), workerId)))
                .doOnNext(marked -> {
                    if (Boolean.TRUE.equals(marked)) {
                        metrics.published(event.eventType());
                    } else {
                        metrics.leaseLost(event.eventType());
                        log.warn("Outbox lease lost while marking {} as published", event.id());
                    }
                })
                .then()
                .onErrorResume(error -> markFailed(claimed, error));
    }

    private Mono<Void> markFailed(OutboxPort.ClaimedEvent claimed, Throwable error) {
        var event = claimed.event();
        int attempt = claimed.attempts() + 1;
        boolean exhausted = attempt >= properties.getMaxAttempts();
        if (exhausted) {
            log.error("Outbox event {} ({}) exhausted its {} attempts",
                    event.id(), event.eventType(), properties.getMaxAttempts(), error);
        } else {
            log.warn("Outbox event {} ({}) failed on attempt {}",
                    event.id(), event.eventType(), attempt, error);
        }
        return outbox.markFailed(event.id(), workerId, properties.getMaxAttempts(),
                        properties.backoff(attempt), errorMessage(error))
                .doOnSuccess(ignored -> {
                    if (exhausted) {
                        metrics.exhausted(event.eventType());
                    } else {
                        metrics.failed(event.eventType());
                    }
                });
    }

    private static String errorMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getName()
                : error.getClass().getName() + ": " + message;
    }
}
