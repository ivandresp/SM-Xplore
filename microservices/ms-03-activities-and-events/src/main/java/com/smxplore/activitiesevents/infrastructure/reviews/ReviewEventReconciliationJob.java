package com.smxplore.activitiesevents.infrastructure.reviews;

import com.smxplore.activitiesevents.application.reviews.ReviewEventMetrics;
import com.smxplore.activitiesevents.application.reviews.ReviewEventPublisher;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Cierra la ventana de pérdida de {@code reviews.review.changed}: la reseña se guarda en MongoDB y
 * el evento en el outbox de PostgreSQL, así que un fallo entre ambas escrituras deja la reseña sin
 * su evento para siempre.
 *
 * <p>El job busca reseñas con {@code eventPublishedAt} nulo (respetando la ventana de gracia para no
 * competir con una escritura en curso) y las re-despacha por el mismo camino que la escritura
 * original. Es idempotente: el {@code eventId} determinista y el {@code ON CONFLICT DO NOTHING} del
 * outbox hacen que un re-despacho de un evento ya aceptado no duplique nada.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewEventReconciliationJob {

    private final ReviewRepositoryPort reviews;
    private final ReviewEventPublisher publisher;
    private final ReviewEventMetrics metrics;
    private final ReviewEventReconciliationProperties properties;

    @Scheduled(fixedDelayString = "${app.reviews.event-reconciliation.interval-ms:60000}",
            initialDelayString = "${app.reviews.event-reconciliation.initial-delay-ms:30000}")
    public Mono<Void> reconcile() {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }
        var cutoff = Instant.now().minusSeconds(properties.getGraceSeconds());
        return reviews.findPendingEventPublication(cutoff, properties.getBatchSize())
                .concatMap(review -> publisher.redispatch(review)
                        .doOnNext(ignored -> metrics.reconciled()))
                .count()
                .filter(count -> count > 0)
                .doOnNext(count -> log.info("Re-dispatched {} reviews without a confirmed event", count))
                .onErrorResume(error -> {
                    log.warn("Review event reconciliation failed: {}", error.toString());
                    return Mono.empty();
                })
                .then();
    }
}
