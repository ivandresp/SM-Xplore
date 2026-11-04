package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewReport;
import com.smxplore.sitesandservices.domain.model.review.ReviewReportStatus;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.domain.ports.in.ResolveReviewReportUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReviewReportRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class ReviewReportResolveService implements ResolveReviewReportUseCase {

    private final ReviewReportRepositoryPort reportRepository;
    private final ReviewRepositoryPort reviewRepository;
    private final CatalogLookup lookup;
    private final CatalogAccess access;
    private final TransactionalOutboxWriter outboxWriter;
    private final DomainEventFactory eventFactory;
    private final Clock clock;

    @Override
    public Mono<ReviewReport> handle(
            UUID reportId,
            ReviewReportStatus status,
            String resolution,
            Actor actor,
            String correlationId
    ) {
        requireResolutionStatus(status);
        return access.requireAdmin(actor)
                .then(lookup.reviewReport(reportId))
                .flatMap(report -> ensureOpen(report)
                        .then(Mono.defer(() -> resolve(report, status, resolution, actor, correlationId))));
    }

    private Mono<ReviewReport> resolve(
            ReviewReport report,
            ReviewReportStatus status,
            String resolution,
            Actor actor,
            String correlationId
    ) {
        Instant now = Instant.now(clock);
        ReviewReport resolved = report.toBuilder()
                .status(status)
                .resolution(resolution)
                .resolvedAt(now)
                .resolvedBy(actor.userId())
                .build();
        resolved.validate();
        Mono<Void> hidden = status == ReviewReportStatus.ACCEPTED
                ? hideReview(report.reviewId(), now, correlationId)
                : Mono.empty();
        return hidden.then(reportRepository.save(resolved));
    }

    private Mono<Void> hideReview(UUID reviewId, Instant now, String correlationId) {
        return lookup.review(reviewId)
                .map(review -> review.toBuilder()
                        .status(ReviewStatus.HIDDEN)
                        .updatedAt(now)
                        .build())
                .flatMap(review -> outboxWriter.save(
                        reviewRepository.save(review),
                        eventFactory::reviewChanged,
                        correlationId))
                .then();
    }

    private static void requireResolutionStatus(ReviewReportStatus status) {
        if (status == null || status == ReviewReportStatus.OPEN) {
            throw new InvalidCatalogException("review report status must be ACCEPTED or DISMISSED");
        }
    }

    private static Mono<ReviewReport> ensureOpen(ReviewReport report) {
        return report.status() == ReviewReportStatus.OPEN
                ? Mono.just(report)
                : Mono.error(new InvalidCatalogException("review report is already resolved"));
    }
}
