package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.ports.in.ChangeReviewStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

@RequiredArgsConstructor
public class ReviewChangeStatusService implements ChangeReviewStatusUseCase {

    private final ReviewRepositoryPort reviewRepository;
    private final CatalogLookup lookup;
    private final CatalogAccess access;
    private final TransactionalOutboxWriter outboxWriter;
    private final DomainEventFactory eventFactory;
    private final Clock clock;

    @Override
    public Mono<Review> handle(Review review, Actor actor, String correlationId) {
        return lookup.review(review.reviewId())
                .flatMap(existing -> access.requireAdmin(existing, actor))
                .flatMap(existing -> {
                    Review updated = existing.toBuilder()
                            .status(review.status())
                            .updatedAt(Instant.now(clock))
                            .build();
                    updated.validate();
                    return outboxWriter.save(
                            reviewRepository.save(updated),
                            eventFactory::reviewChanged,
                            correlationId
                    );
                });
    }
}
