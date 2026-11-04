package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.review.ReviewReport;
import com.smxplore.sitesandservices.domain.model.review.ReviewReportStatus;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ResolveReviewReportUseCase {

    Mono<ReviewReport> handle(
            UUID reportId,
            ReviewReportStatus status,
            String resolution,
            Actor actor,
            String correlationId
    );
}
