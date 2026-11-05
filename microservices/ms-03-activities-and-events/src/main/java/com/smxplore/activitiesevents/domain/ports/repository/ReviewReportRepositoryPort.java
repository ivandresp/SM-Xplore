package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

public interface ReviewReportRepositoryPort {

    Mono<ReviewReport> save(ReviewReport report);

    Mono<ReviewReport> findById(UUID reportId);

    /** Administrative queue, oldest report first, bounded by {@code limit} in the database. */
    Flux<ReviewReport> findByStatus(ReviewReportStatus status, int limit);

    /** Reports of the given reviews, used to scope the list to a manager's catalog or a guide. */
    Flux<ReviewReport> findByStatusAndReviewIds(ReviewReportStatus status, Collection<UUID> reviewIds, int limit);

    Mono<Boolean> existsByReviewAndReporter(UUID reviewId, UUID reporterId);
}
