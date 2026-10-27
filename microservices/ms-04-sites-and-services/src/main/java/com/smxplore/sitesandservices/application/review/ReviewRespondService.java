package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.ports.in.RespondToReviewUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

@RequiredArgsConstructor
public class ReviewRespondService implements RespondToReviewUseCase {

    private final ReviewRepositoryPort reviewRepository;
    private final CatalogLookup lookup;
    private final CatalogAccess access;
    private final Clock clock;

    @Override
    public Mono<Review> handle(Review review, Actor actor) {
        return requireResponse(review.response())
                .then(lookup.review(review.reviewId()))
                .flatMap(existing -> lookup.reviewTarget(existing.itemType(), existing.itemId())
                        .flatMap(target -> access.requireTargetOwner(target, actor))
                        .thenReturn(existing))
                .flatMap(existing -> {
                    Instant now = Instant.now(clock);
                    Review updated = existing.toBuilder()
                            .response(review.response())
                            .respondedAt(now)
                            .updatedAt(now)
                            .build();
                    updated.validate();
                    return reviewRepository.save(updated);
                });
    }

    private Mono<Void> requireResponse(String response) {
        return response == null || response.isBlank()
                ? Mono.error(new InvalidCatalogException("review response is required"))
                : Mono.empty();
    }
}
