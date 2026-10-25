package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo;

import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewReportRepositoryPort;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document.ReviewReportDocument;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.repository.ReviewReportMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReviewReportMongoAdapter implements ReviewReportRepositoryPort {

    private final ReviewReportMongoRepository repository;

    @Override
    public Mono<ReviewReport> save(ReviewReport report) {
        return repository.save(toDocument(report)).map(this::toDomain);
    }

    @Override
    public Mono<ReviewReport> findById(UUID reportId) {
        return repository.findById(reportId).map(this::toDomain);
    }

    @Override
    public Flux<ReviewReport> findByStatus(ReviewReportStatus status) {
        return repository.findByStatusOrderByCreatedAtAsc(status).map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsByReviewAndReporter(UUID reviewId, UUID reporterId) {
        return repository.existsByReviewAndReporter(reviewId, reporterId);
    }

    private ReviewReportDocument toDocument(ReviewReport report) {
        return new ReviewReportDocument(report.id(), report.reviewId(), report.reporterId(), report.reason(),
                report.status(), report.resolvedBy(), report.resolvedAt(), report.createdAt());
    }

    private ReviewReport toDomain(ReviewReportDocument document) {
        return new ReviewReport(document.id(), document.reviewId(), document.reporterId(), document.reason(),
                document.status(), document.resolvedBy(), document.resolvedAt(), document.createdAt());
    }
}
