package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.exceptions.review.InvalidReviewException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewAlreadyReportedException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewReportLimitExceededException;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewReportRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ReportReviewUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Denuncia de una resena publicada. Solo se aceptan resenas visibles (las ocultas, borradas o
 * pendientes de moderacion responden como inexistentes para no servir de oraculo de existencia) y
 * cada cuenta tiene una cuota diaria: sin ella una sola cuenta podia inundar la cola de moderacion
 * con una denuncia por cada resena del catalogo.
 */
@RequiredArgsConstructor
public class ReportReviewUseCase implements ReportReviewUseCasePort {

    private static final int REASON_MAX_LENGTH = 500;
    private static final int MAX_REPORTS_PER_DAY = 10;
    private static final Duration REPORTER_WINDOW = Duration.ofDays(1);

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
        var windowStart = Instant.now().minus(REPORTER_WINDOW);
        return reviewRepo.findById(reviewId)
                .filter(review -> ReviewStatus.PUBLISHED == review.getStatus())
                .switchIfEmpty(Mono.error(new ReviewNotFoundException(reviewId)))
                .flatMap(review -> reportRepo.countByReporterSince(reporterId, windowStart)
                        .defaultIfEmpty(0L)
                        .flatMap(count -> {
                            if (count >= MAX_REPORTS_PER_DAY) {
                                return Mono.<ReviewReport>error(new ReviewReportLimitExceededException(
                                        "Too many review reports in the last 24 hours."));
                            }
                            return reportRepo.existsByReviewAndReporter(reviewId, reporterId)
                                    .flatMap(exists -> exists
                                            ? Mono.<ReviewReport>error(new ReviewAlreadyReportedException(
                                                    "The user already reported this review."))
                                            : reportRepo.save(ReviewReport.pending(reviewId, reporterId, reason)));
                        })
                        .thenReturn(review));
    }
}
