package com.smxplore.activitiesevents.domain.ports.usecases.review;

import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import reactor.core.publisher.Flux;

public interface ConsultReviewReportsUseCasePort {

    Flux<ReviewReport> handle(ReviewReportStatus status, int limit);
}
