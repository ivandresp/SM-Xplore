package com.smxplore.activitiesevents.domain.ports.usecases.review;

import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CreateReviewUseCasePort {

    Mono<Review> handle(UUID itemId, ReviewItemType itemType, UUID userId, String userName,
                        int rating, String comment);
}
