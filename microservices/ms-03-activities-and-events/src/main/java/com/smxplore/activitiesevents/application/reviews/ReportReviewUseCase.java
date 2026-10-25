package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.exceptions.review.InvalidReviewException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewAlreadyReportedException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewNotFoundException;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewReportRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ReportReviewUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class ReportReviewUseCase implements ReportReviewUseCasePort {

    private static final int REASON_MAX_LENGTH = 500;

    private final ReviewRepositoryPort reviewRepo;
    private final ReviewReportRepositoryPort reportRepo;

    @Override
    public Mono<Review> handle(UUID reviewId, UUID reporterId, String reason) {
        if (reason == null || reason.isBlank()) {
            return Mono.error(new InvalidReviewException("Report reason must not be blank."));
        }
        if (reason.length() > REASON_MAX_LENGTH) {
            return Mono.error(new InvalidReviewException(
                    "Report reason must not exceed %d characters.".formatted(REASON_MAX_LENGTH)));
        }
        if (reporterId == null) {
            return Mono.error(new InvalidReviewException("Reporter is mandatory."));
        }
        return reviewRepo.findById(reviewId)
                .switchIfEmpty(Mono.error(new ReviewNotFoundException(reviewId)))
                .flatMap(review -> reportRepo.existsByReviewAndReporter(reviewId, reporterId)
                        .flatMap(exists -> exists
                                ? Mono.error(new ReviewAlreadyReportedException(
                                        "The user already reported this review."))
                                : reportRepo.save(new ReviewReport(UUID.randomUUID(), reviewId, reporterId,
                                        reason, Instant.now())).thenReturn(review)));
    }
}
