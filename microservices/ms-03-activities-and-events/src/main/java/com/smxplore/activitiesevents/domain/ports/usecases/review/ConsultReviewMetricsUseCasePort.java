package com.smxplore.activitiesevents.domain.ports.usecases.review;

import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewMetrics;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConsultReviewMetricsUseCasePort {

    Mono<ReviewMetrics> handle(UUID itemId, ReviewItemType itemType);
}
