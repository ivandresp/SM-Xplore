package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewMetrics;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ReviewRepositoryPort {

    Mono<Review> save(Review review);

    Mono<Review> findById(UUID reviewId);

    Flux<Review> findByItem(UUID itemId, ReviewItemType itemType);

    Mono<Boolean> existsByItemAndUser(UUID itemId, ReviewItemType itemType, UUID userId);

    Mono<ReviewMetrics> metrics(UUID itemId, ReviewItemType itemType);
}
