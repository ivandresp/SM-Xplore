package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.exceptions.review.InvalidReviewException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewNotFoundException;
import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewReportRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ResolveReviewReportUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ResolveReviewReportUseCase implements ResolveReviewReportUseCasePort {

    private final ReviewReportRepositoryPort reportRepo;

    @Override
    public Mono<ReviewReport> handle(UUID reportId, ReviewReportStatus status, UUID resolverId) {
        if (status != ReviewReportStatus.RESOLVED && status != ReviewReportStatus.DISMISSED) {
            return Mono.error(new InvalidReviewException("Report can only be resolved or dismissed."));
        }
        return reportRepo.findById(reportId)
                .switchIfEmpty(Mono.error(new ReviewNotFoundException(reportId)))
                .flatMap(report -> {
                    if (report.status() != ReviewReportStatus.PENDING) {
                        return Mono.error(new InvalidReviewException("The report was already resolved."));
                    }
                    return reportRepo.save(report.resolve(status, resolverId));
                });
    }
}
