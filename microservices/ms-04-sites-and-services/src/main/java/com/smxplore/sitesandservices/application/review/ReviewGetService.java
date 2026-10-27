package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.ports.in.GetReviewUseCase;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ReviewGetService implements GetReviewUseCase {

    private final CatalogLookup lookup;

    @Override
    public Mono<Review> handle(UUID reviewId) {
        return lookup.review(reviewId);
    }
}
