package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.review.ReviewReport;
import reactor.core.publisher.Mono;

public interface ReportReviewUseCase {

    Mono<ReviewReport> handle(ReviewReport report, Actor actor);
}
