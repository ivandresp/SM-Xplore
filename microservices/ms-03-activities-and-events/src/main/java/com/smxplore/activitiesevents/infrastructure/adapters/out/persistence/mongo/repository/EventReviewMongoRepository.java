package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.repository;

import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document.EventReviewDocument;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.projection.ReviewMetricsProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

public interface EventReviewMongoRepository extends ReactiveMongoRepository<EventReviewDocument, UUID> {

    /**
     * Public listing of published reviews. {@code status: null} also matches documents written
     * before the field existed, which were published by definition.
     */
    @Query(value = "{ 'itemId': ?0, '$or': [ { 'status': 'PUBLISHED' }, { 'status': null } ] }",
            sort = "{ 'createdAt': -1 }")
    Flux<EventReviewDocument> findPublishedByItem(UUID itemId);

    Flux<EventReviewDocument> findByItemIdIn(Collection<UUID> itemIds);

    /**
     * Administrative queue, newest update first. The limit travels in the query: without it Mongo
     * streams the whole status of the collection and the service trims it in memory.
     */
    Flux<EventReviewDocument> findByStatusOrderByUpdatedAtDescIdAsc(ReviewStatus status, Pageable pageable);

    @Query(value = "{ 'itemId': ?0, 'user.id': ?1 }", exists = true)
    Mono<Boolean> existsByItemAndUser(UUID itemId, UUID userId);

    @Aggregation(pipeline = {
            "{ '$match': { 'itemId': ?0, '$or': [ { 'status': 'PUBLISHED' }, { 'status': null } ] } }",
            "{ '$group': { '_id': null, 'count': { '$sum': 1 }, 'average': { '$avg': '$rating' } } }",
            "{ '$project': { '_id': 0, 'count': 1, 'average': 1 } }"
    })
    Mono<ReviewMetricsProjection> aggregateMetrics(UUID itemId);
}
