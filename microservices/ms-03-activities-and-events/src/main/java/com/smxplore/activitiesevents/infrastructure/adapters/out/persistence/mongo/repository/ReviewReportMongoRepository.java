package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.repository;

import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document.ReviewReportDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface ReviewReportMongoRepository extends ReactiveMongoRepository<ReviewReportDocument, UUID> {

    /** Oldest first so the queue is attended in arrival order; the limit travels to Mongo. */
    Flux<ReviewReportDocument> findByStatusOrderByCreatedAtAsc(ReviewReportStatus status, Pageable pageable);

    Flux<ReviewReportDocument> findByStatusAndReviewIdInOrderByCreatedAtAsc(
            ReviewReportStatus status,
            Collection<UUID> reviewIds,
            Pageable pageable);

    @Query(value = "{ 'reviewId': ?0, 'reporterId': ?1 }", exists = true)
    Mono<Boolean> existsByReviewAndReporter(UUID reviewId, UUID reporterId);

    /** Cuota por cuenta: cuenta solo los documentos del denunciante en la ventana pedida. */
    Mono<Long> countByReporterIdAndCreatedAtGreaterThanEqual(UUID reporterId, Instant since);
}
