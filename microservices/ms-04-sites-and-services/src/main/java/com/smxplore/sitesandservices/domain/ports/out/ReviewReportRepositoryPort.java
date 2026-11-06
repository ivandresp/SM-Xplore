package com.smxplore.sitesandservices.domain.ports.out;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.review.ReviewReport;
import com.smxplore.sitesandservices.domain.model.review.ReviewReportFilter;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ReviewReportRepositoryPort {

    Mono<ReviewReport> save(ReviewReport report);

    Mono<ReviewReport> findById(UUID reportId);

    Mono<ReviewReport> findByReviewAndReporter(UUID reviewId, UUID reportedBy);

    Mono<PageResult<ReviewReport>> findAll(ReviewReportFilter filter);

    Mono<Long> countOpenByReview(UUID reviewId);
}
