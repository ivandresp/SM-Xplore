package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewReportRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ConsultReviewReportsUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class ConsultReviewReportsUseCase implements ConsultReviewReportsUseCasePort {

    private final ReviewReportRepositoryPort reportRepo;

    @Override
    public Flux<ReviewReport> handle(ReviewReportStatus status, int limit) {
        return reportRepo.findByStatus(status).take(limit);
    }
}
