package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.exception.DuplicateReviewException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.review.CatalogTarget;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.domain.ports.in.CreateReviewUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class ReviewCreateService implements CreateReviewUseCase {

    private final ReviewRepositoryPort reviewRepository;
    private final CatalogLookup lookup;
    private final CatalogAccess access;
    private final TransactionalOutboxWriter outboxWriter;
    private final DomainEventFactory eventFactory;
    private final Clock clock;

    @Override
    public Mono<Review> handle(Review review, Actor actor, String correlationId) {
        return access.requireTourist(actor)
                .then(lookup.publishedReviewTarget(review.itemType(), review.itemId()))
                .flatMap(target -> ensureNoDuplicate(actor.userId(), target)
                        .then(Mono.defer(() -> create(review, actor, target, correlationId))));
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
        return outboxWriter.save(
                reviewRepository.save(created),
                eventFactory::reviewChanged,
                correlationId
        );
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
