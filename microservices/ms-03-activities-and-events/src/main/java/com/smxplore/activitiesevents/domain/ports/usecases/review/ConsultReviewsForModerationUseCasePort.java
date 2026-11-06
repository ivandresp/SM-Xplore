package com.smxplore.activitiesevents.domain.ports.usecases.review;

import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;
import reactor.core.publisher.Flux;

public interface ConsultReviewsForModerationUseCasePort {

    Flux<Review> handle(ReviewStatus status, int limit);
}
