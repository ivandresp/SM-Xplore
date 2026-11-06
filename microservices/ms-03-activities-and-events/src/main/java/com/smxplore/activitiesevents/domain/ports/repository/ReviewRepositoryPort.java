package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewMetrics;
import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface ReviewRepositoryPort {

    Mono<Review> save(Review review);

    Mono<Review> findById(UUID reviewId);

    /** Public listing: only {@link ReviewStatus#PUBLISHED} reviews, newest first. */
    Flux<Review> findByItem(UUID itemId, ReviewItemType itemType);

    /**
     * Reviews of several items of the same type, used to scope reports to a manager's catalog.
     * Hidden reviews are included on purpose so their pending reports remain resolvable.
     */
    Flux<Review> findByItemIds(Collection<UUID> itemIds, ReviewItemType itemType);

    Mono<Boolean> existsByItemAndUser(UUID itemId, ReviewItemType itemType, UUID userId);

    /** Administrative listing across both collections, newest update first. */
    Flux<Review> findByStatus(ReviewStatus status, int limit);

    /** Metrics only count {@link ReviewStatus#PUBLISHED} reviews. */
    Mono<ReviewMetrics> metrics(UUID itemId, ReviewItemType itemType);

    /**
     * Reviews whose {@code reviews.review.changed} never reached the outbox, oldest update first.
     * Only reviews updated before {@code updatedBefore} are returned: that grace window keeps the
     * job from racing a write that is still enqueueing its event.
     */
    Flux<Review> findPendingEventPublication(Instant updatedBefore, int limit);

    /** Records that the outbox accepted the review's event. Returns false when nothing matched. */
    Mono<Boolean> markEventPublished(UUID reviewId, Instant publishedAt);
}
