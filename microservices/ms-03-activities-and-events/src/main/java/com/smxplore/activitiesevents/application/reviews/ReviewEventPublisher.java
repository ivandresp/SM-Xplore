package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.application.outbox.ReviewEventFactory;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Publishes {@code reviews.review.changed} after a review write.
 *
 * <p>The review lives in MongoDB and the outbox in PostgreSQL, so the two writes cannot share a
 * transaction. The review is saved first; if enqueueing fails the error is logged and the saved
 * review is still returned, because the user's write already succeeded. Nothing retries that event,
 * so a review without its event has to be reconciled out of band.
 *
 * TODO(ms-03): pending contract to close this window. Either the outbox moves to MongoDB (same
 * transaction as the review) or a reconciliation job compares review_reviews/event_reviews with
 * outbox_events and re-enqueues the missing reviews.review.changed. Until then this is the only
 * place that can leave a review without its event, and the log is the only trace.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewEventPublisher {

    private final OutboxPort outbox;
    private final ReviewEventFactory eventFactory;

    public Mono<Review> publish(Review review) {
        return eventFactory.reviewChanged(review)
                .flatMap(outbox::enqueue)
                .thenReturn(review)
                .onErrorResume(error -> {
                    log.error("Could not enqueue reviews.review.changed for review {}",
                            review.getId(), error);
                    return Mono.just(review);
                });
    }
}
