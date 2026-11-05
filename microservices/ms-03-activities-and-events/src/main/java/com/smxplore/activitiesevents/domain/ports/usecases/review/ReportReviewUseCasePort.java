package com.smxplore.activitiesevents.domain.ports.usecases.review;

import com.smxplore.activitiesevents.domain.model.review.Review;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ReportReviewUseCasePort {

    Mono<Review> handle(UUID reviewId, UUID reporterId, String reason);
}
