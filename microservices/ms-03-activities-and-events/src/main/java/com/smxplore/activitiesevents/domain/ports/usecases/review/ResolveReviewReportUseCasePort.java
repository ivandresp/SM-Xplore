package com.smxplore.activitiesevents.domain.ports.usecases.review;

import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ResolveReviewReportUseCasePort {

    Mono<ReviewReport> handle(UUID reportId, ReviewReportStatus status, UUID resolverId);
}
