package com.smxplore.activitiesevents.domain.ports.usecases.review;

import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ModerateReviewUseCasePort {

    Mono<Review> handle(UUID reviewId, ReviewStatus status, UUID moderatorId, String role);
}
