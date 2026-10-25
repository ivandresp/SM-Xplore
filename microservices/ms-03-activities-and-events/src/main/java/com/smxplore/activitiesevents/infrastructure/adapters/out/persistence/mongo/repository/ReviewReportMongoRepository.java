package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.repository;

import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document.ReviewReportDocument;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ReviewReportMongoRepository extends ReactiveMongoRepository<ReviewReportDocument, UUID> {

    Flux<ReviewReportDocument> findByStatusOrderByCreatedAtAsc(
            com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus status);

    @Query(value = "{ 'reviewId': ?0, 'reporterId': ?1 }", exists = true)
    Mono<Boolean> existsByReviewAndReporter(UUID reviewId, UUID reporterId);
}
