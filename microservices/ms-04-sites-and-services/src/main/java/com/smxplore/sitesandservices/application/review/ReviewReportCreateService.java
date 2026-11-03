package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.application.support.CatalogLookup;
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
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class ReviewReportCreateService implements ReportReviewUseCase {

    private final ReviewReportRepositoryPort reportRepository;
    private final CatalogLookup lookup;
    private final Clock clock;

    @Override
    public Mono<ReviewReport> handle(ReviewReport report, Actor actor) {
        return lookup.review(report.reviewId())
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
