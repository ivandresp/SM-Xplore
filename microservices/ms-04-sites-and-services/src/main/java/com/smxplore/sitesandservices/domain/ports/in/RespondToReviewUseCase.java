package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.review.Review;
import reactor.core.publisher.Mono;

public interface RespondToReviewUseCase {

    Mono<Review> handle(Review review, Actor actor);
}
