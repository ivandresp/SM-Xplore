package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ReviewReportRepositoryPort {

    Mono<ReviewReport> save(ReviewReport report);

    Mono<Boolean> existsByReviewAndReporter(UUID reviewId, UUID reporterId);
}
