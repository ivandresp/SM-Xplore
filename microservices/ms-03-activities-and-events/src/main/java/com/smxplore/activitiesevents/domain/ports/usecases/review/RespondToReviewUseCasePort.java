package com.smxplore.activitiesevents.domain.ports.usecases.review;

import com.smxplore.activitiesevents.domain.model.review.Review;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface RespondToReviewUseCasePort {

    Mono<Review> handle(UUID reviewId, UUID responderId, String role, String message);
}
