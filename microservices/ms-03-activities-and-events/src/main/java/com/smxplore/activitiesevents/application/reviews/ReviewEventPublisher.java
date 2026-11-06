package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.application.outbox.ReviewEventFactory;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Publica {@code reviews.review.changed} después de escribir la reseña y registra en la propia
 * reseña que el evento quedó confirmado.
 *
 * <p>La reseña vive en MongoDB y el outbox en PostgreSQL, así que las dos escrituras no comparten
 * transacción. El orden es deliberado: primero se encola el evento y solo si el outbox lo acepta se
 * marca {@code eventPublishedAt}. Si algo falla, el error se registra, la petición del usuario sigue
 * devolviendo la reseña guardada y el job de conciliación encontrará la reseña con
 * {@code eventPublishedAt} nulo y la re-despachará. El {@code eventId} determinista hace que ese
 * re-despacho sea idempotente aunque el enqueue original sí hubiera llegado a confirmarse.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewEventPublisher {

    private final OutboxPort outbox;
    private final ReviewEventFactory eventFactory;
    private final ReviewRepositoryPort reviews;
    private final ReviewEventMetrics metrics;

    /**
     * Camino normal tras una escritura de reseña. Nunca falla: la reseña ya está guardada y perder
     * la respuesta al usuario por un problema del outbox sería peor que perder el evento (que se
     * reconcilia después).
     */
    public Mono<Review> publish(Review review) {
        return dispatch(review);
    }

    /** Mismo camino que {@link #publish(Review)}, usado por el job de conciliación. */
    public Mono<Review> redispatch(Review review) {
        return dispatch(review);
    }

    private Mono<Review> dispatch(Review review) {
        return eventFactory.reviewChanged(review)
                .flatMap(event -> outbox.enqueue(event)
                        // Diferido a proposito: si el enqueue falla, el marcado ni se construye.
                        .then(Mono.defer(() -> markPublished(review))))
                .thenReturn(review)
                .doOnNext(ignored -> metrics.published())
                .onErrorResume(error -> {
                    log.error("Could not enqueue reviews.review.changed for review {}; left pending for"
                            + " reconciliation", review.getId(), error);
                    metrics.failed();
                    return Mono.just(review);
                });
    }

    private Mono<Void> markPublished(Review review) {
        var publishedAt = Instant.now();
        return reviews.markEventPublished(review.getId(), publishedAt)
                .doOnNext(updated -> {
                    if (!updated) {
                        // El documento desapareció entre el guardado y el enqueue: no hay nada que
                        // marcar y el evento ya está en el outbox.
                        log.warn("Review {} could not be marked as event-published", review.getId());
                    }
                })
                .then();
    }
}
