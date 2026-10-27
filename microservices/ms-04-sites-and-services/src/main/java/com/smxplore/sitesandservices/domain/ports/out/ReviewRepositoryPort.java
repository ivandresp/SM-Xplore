package com.smxplore.sitesandservices.domain.ports.out;

import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewFilter;
import com.smxplore.sitesandservices.domain.model.review.ReviewSummary;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ReviewRepositoryPort {

    Mono<Review> save(Review review);

    Mono<Review> findById(UUID reviewId);

    Mono<Review> findByTouristAndItem(UUID touristId, ItemType itemType, UUID itemId);

    Mono<PageResult<Review>> findAll(ReviewFilter filter);

    Mono<ReviewSummary> summarize(ItemType itemType, UUID itemId);
}
