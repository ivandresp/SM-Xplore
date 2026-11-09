package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.InMemoryRateLimiter;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.exception.DuplicateReviewException;
import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.exception.ReviewFilterUnavailableException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.review.CatalogTarget;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewAiAssessment;
import com.smxplore.sitesandservices.domain.model.review.ReviewAiPolicy;
import com.smxplore.sitesandservices.domain.model.review.ReviewAiRequest;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.domain.ports.in.CreateReviewUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReservationRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ReviewAiFilter;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ReviewCreateService implements CreateReviewUseCase {

    private static final int REVIEWS_PER_HOUR = 10;

    private final ReviewRepositoryPort reviewRepository;
    private final CatalogLookup lookup;
    private final CatalogAccess access;
    private final ReviewAiFilter aiFilter;
    private final ReviewAiPolicy aiPolicy;
    private final TransactionalOutboxWriter outboxWriter;
    private final DomainEventFactory eventFactory;
    private final Clock clock;
    private final ReservationRepositoryPort reservationRepository;
    private final ServiceRepositoryPort serviceRepository;
    private final boolean requireCompletedBooking;
    private final InMemoryRateLimiter rateLimiter =
            new InMemoryRateLimiter(REVIEWS_PER_HOUR, Duration.ofHours(1));

    @Override
    public Mono<Review> handle(Review review, Actor actor, String correlationId) {
        if (actor == null) {
            return Mono.error(new ForbiddenOperationException(
                    "Authentication is required to create a review"));
        }
        return access.requireTourist(actor)
                .then(rateLimiter.acquire("review-create:" + actor.userId()))
                .then(lookup.publishedReviewTarget(review.itemType(), review.itemId()))
                .flatMap(target -> ensureCompletedBooking(actor.userId(), target)
                        .then(Mono.defer(() -> ensureNoDuplicate(actor.userId(), target)))
                        .then(Mono.defer(() -> create(review, actor, target, correlationId))));
    }

    /**
     * MS04-12: una reseña solo se acepta si el turista tiene una reserva COMPLETED del servicio
     * (o de algún servicio del sitio, cuando se reseña un SITE). El control es configurable para
     * poder desactivarlo en tests, pero por defecto esta activo en todos los perfiles.
     */
    private Mono<Void> ensureCompletedBooking(UUID touristId, CatalogTarget target) {
        if (!requireCompletedBooking) {
            return Mono.empty();
        }
        Mono<Boolean> completed = target.itemType() == ItemType.SITE
                ? serviceRepository.findIdsBySiteId(target.itemId())
                        .collectList()
                        .flatMap(serviceIds -> serviceIds.isEmpty()
                                ? Mono.just(false)
                                : reservationRepository.existsCompletedBooking(touristId, serviceIds))
                : reservationRepository.existsCompletedBooking(
                        touristId, List.of(target.itemId()));
        return completed.flatMap(exists -> exists
                ? Mono.empty()
                : Mono.error(new ForbiddenOperationException(
                        "Only tourists with a completed reservation can review this item")));
    }

    private Mono<Review> create(
            Review review,
            Actor actor,
            CatalogTarget target,
            String correlationId
    ) {
        Instant now = Instant.now(clock);
        Review created = review.toBuilder()
                .reviewId(UUID.randomUUID())
                .touristId(actor.userId())
                .touristName(actor.displayName())
                .itemName(target.name())
                .status(ReviewStatus.PUBLISHED)
                .createdAt(now)
                .updatedAt(now)
                .build();
        created.validate();
        return assess(created, now)
                .flatMap(assessed -> outboxWriter.save(
                        reviewRepository.save(assessed),
                        eventFactory::reviewChanged,
                        correlationId
                ));
    }

    private Mono<Review> assess(Review review, Instant now) {
        if (review.commentary() == null || review.commentary().isBlank()) {
            return Mono.just(review.withAiAssessment(
                    ReviewAiAssessment.skipped(now),
                    aiPolicy,
                    now));
        }
        ReviewAiRequest request = new ReviewAiRequest(
                review.itemType().name(),
                review.rating(),
                review.commentary());
        return aiFilter.assess(request)
                .onErrorResume(error -> Mono.just(ReviewAiAssessment.unavailable(now)))
                .flatMap(assessment -> {
                    if (aiPolicy.shouldRejectOnFailure(assessment)) {
                        return Mono.error(new ReviewFilterUnavailableException());
                    }
                    return Mono.just(review.withAiAssessment(assessment, aiPolicy, now));
                });
    }

    private Mono<Void> ensureNoDuplicate(UUID touristId, CatalogTarget target) {
        return reviewRepository.findByTouristAndItem(touristId, target.itemType(), target.itemId())
                .flatMap(existing -> Mono.<Void>error(new DuplicateReviewException(
                        touristId,
                        target.itemType().name(),
                        target.itemId()
                )))
                .then();
    }
}
