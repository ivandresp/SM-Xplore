package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ReviewReportRepositoryPort {

    Mono<ReviewReport> save(ReviewReport report);

    Mono<ReviewReport> findById(UUID reportId);

    Flux<ReviewReport> findByStatus(ReviewReportStatus status);

    Mono<Boolean> existsByReviewAndReporter(UUID reviewId, UUID reporterId);
}
