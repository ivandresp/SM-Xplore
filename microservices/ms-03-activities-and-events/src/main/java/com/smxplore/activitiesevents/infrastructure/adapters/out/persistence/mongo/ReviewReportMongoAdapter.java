package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo;

import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewReportRepositoryPort;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document.ReviewReportDocument;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.repository.ReviewReportMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReviewReportMongoAdapter implements ReviewReportRepositoryPort {

    private final ReviewReportMongoRepository repository;

    @Override
    public Mono<ReviewReport> save(ReviewReport report) {
        return repository.save(new ReviewReportDocument(report.id(), report.reviewId(), report.reporterId(),
                        report.reason(), report.createdAt()))
                .map(document -> new ReviewReport(document.id(), document.reviewId(), document.reporterId(),
                        document.reason(), document.createdAt()));
    }

    @Override
    public Mono<Boolean> existsByReviewAndReporter(UUID reviewId, UUID reporterId) {
        return repository.existsByReviewAndReporter(reviewId, reporterId);
    }
}
