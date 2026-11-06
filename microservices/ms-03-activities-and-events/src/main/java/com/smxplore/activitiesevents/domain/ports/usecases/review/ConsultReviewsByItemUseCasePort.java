package com.smxplore.activitiesevents.domain.ports.usecases.review;

import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ConsultReviewsByItemUseCasePort {

    Flux<Review> handle(UUID itemId, ReviewItemType itemType, int limit);
}
