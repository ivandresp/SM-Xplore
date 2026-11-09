package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.InMemoryRateLimiter;
import com.smxplore.sitesandservices.domain.exception.DuplicateReviewReportException;
import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewReport;
import com.smxplore.sitesandservices.domain.model.review.ReviewReportStatus;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.domain.ports.in.ReportReviewUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReviewReportRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class ReviewReportCreateService implements ReportReviewUseCase {

    private static final int REPORTS_PER_HOUR = 20;

    private final ReviewReportRepositoryPort reportRepository;
    private final CatalogLookup lookup;
    private final Clock clock;
    private final InMemoryRateLimiter rateLimiter =
            new InMemoryRateLimiter(REPORTS_PER_HOUR, Duration.ofHours(1));

    @Override
    public Mono<ReviewReport> handle(ReviewReport report, Actor actor) {
        if (actor == null) {
            return Mono.error(new ForbiddenOperationException(
                    "Authentication is required to report a review"));
        }
        return rateLimiter.acquire("review-report:" + actor.userId())
                .then(lookup.review(report.reviewId()))
                .flatMap(review -> ensureReportable(review, actor)
                        .then(Mono.defer(() -> create(report, review, actor))));
    }

    private Mono<Void> ensureReportable(Review review, Actor actor) {
        if (review.status() == ReviewStatus.DELETED) {
            return Mono.error(new InvalidCatalogException("A deleted review cannot be reported"));
        }
        if (review.touristId().equals(actor.userId())) {
            return Mono.error(new ForbiddenOperationException("Tourists cannot report their own review"));
        }
        return reportRepository.findByReviewAndReporter(review.reviewId(), actor.userId())
                .flatMap(existing -> Mono.<Void>error(
                        new DuplicateReviewReportException(review.reviewId(), actor.userId())))
                .then();
    }

    private Mono<ReviewReport> create(ReviewReport report, Review review, Actor actor) {
        ReviewReport created = report.toBuilder()
                .reportId(UUID.randomUUID())
                .itemType(review.itemType())
                .itemId(review.itemId())
                .reportedBy(actor.userId())
                .reportedByName(actor.displayName())
                .status(ReviewReportStatus.OPEN)
                .createdAt(Instant.now(clock))
                .build();
        created.validate();
        return reportRepository.save(created);
    }
}
