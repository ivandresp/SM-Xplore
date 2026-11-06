package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.review.Review;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GetReviewUseCase {

    Mono<Review> handle(UUID reviewId, Actor actor);
}
