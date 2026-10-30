package com.smxplore.users.application.port.out;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.ReviewStatistics;
import com.smxplore.users.domain.model.ReputationSummary;
import com.smxplore.users.domain.model.Review;
import com.smxplore.users.domain.model.ReviewTargetType;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ReviewRepository {

    Mono<Review> save(Review review);

    Mono<Review> findById(String reviewId);

    Mono<Boolean> existsByTouristAndTarget(UUID touristId, ReviewTargetType targetType, UUID targetId);

    Mono<PageResult<Review>> findVisible(ReviewTargetType targetType, UUID targetId, int page, int size);

    Mono<PageResult<Review>> findReported(int page, int size);

    Mono<PageResult<Review>> findAiFlagged(int page, int size);

    Mono<ReputationSummary> summary(ReviewTargetType targetType, UUID targetId);

    Mono<ReviewStatistics> statistics();
}
