package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo;

import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewAiAssessment;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewMetrics;
import com.smxplore.activitiesevents.domain.model.review.ReviewReply;
import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;
import com.smxplore.activitiesevents.domain.model.review.ReviewUser;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document.ActivityReviewDocument;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document.EventReviewDocument;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document.ReviewAiAssessmentDocument;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document.ReviewReplyDocument;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document.ReviewUserDocument;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.repository.ActivityReviewMongoRepository;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.repository.EventReviewMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.UUID;

/**
 * One collection per review target: event_reviews and activity_reviews.
 */
@Repository
@RequiredArgsConstructor
public class ReviewMongoAdapter implements ReviewRepositoryPort {

    private final EventReviewMongoRepository eventReviews;
    private final ActivityReviewMongoRepository activityReviews;

    @Override
    public Mono<Review> save(Review review) {
        return switch (review.getItemType()) {
            case EVENT -> eventReviews.save(toEventDocument(review)).map(this::toEventDomain);
            case ACTIVITY -> activityReviews.save(toActivityDocument(review)).map(this::toActivityDomain);
        };
    }

    @Override
    public Mono<Review> findById(UUID reviewId) {
        return eventReviews.findById(reviewId).map(this::toEventDomain)
                .switchIfEmpty(activityReviews.findById(reviewId).map(this::toActivityDomain));
    }

    @Override
    public Flux<Review> findByItem(UUID itemId, ReviewItemType itemType) {
        return switch (itemType) {
            case EVENT -> eventReviews.findPublishedByItem(itemId).map(this::toEventDomain);
            case ACTIVITY -> activityReviews.findPublishedByItem(itemId).map(this::toActivityDomain);
        };
    }

    @Override
    public Flux<Review> findByItemIds(Collection<UUID> itemIds, ReviewItemType itemType) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Flux.empty();
        }
        return switch (itemType) {
            case EVENT -> eventReviews.findByItemIdIn(itemIds).map(this::toEventDomain);
            case ACTIVITY -> activityReviews.findByItemIdIn(itemIds).map(this::toActivityDomain);
        };
    }

    @Override
    public Mono<Boolean> existsByItemAndUser(UUID itemId, ReviewItemType itemType, UUID userId) {
        return switch (itemType) {
            case EVENT -> eventReviews.existsByItemAndUser(itemId, userId);
            case ACTIVITY -> activityReviews.existsByItemAndUser(itemId, userId);
        };
    }

    /**
     * Administrative queue. Each collection is asked for {@code limit} rows, not for every row of
     * the status: the page of one collection bounds the merge, so the result is still the global
     * newest {@code limit} without materialising both collections in memory.
     */
    @Override
    public Flux<Review> findByStatus(ReviewStatus status, int limit) {
        if (status == null || limit < 1) {
            return Flux.empty();
        }
        var page = PageRequest.of(0, limit);
        return Flux.merge(
                        eventReviews.findByStatusOrderByUpdatedAtDescIdAsc(status, page).map(this::toEventDomain),
                        activityReviews.findByStatusOrderByUpdatedAtDescIdAsc(status, page).map(this::toActivityDomain))
                // Orden estable: dos reseñas con el mismo updatedAt no deben alternar entre llamadas.
                .sort(Comparator.comparing(Review::updatedAt).reversed().thenComparing(Review::getId))
                .take(limit);
    }

    @Override
    public Mono<ReviewMetrics> metrics(UUID itemId, ReviewItemType itemType) {
        var aggregate = switch (itemType) {
            case EVENT -> eventReviews.aggregateMetrics(itemId);
            case ACTIVITY -> activityReviews.aggregateMetrics(itemId);
        };
        return aggregate
                .map(result -> new ReviewMetrics(itemId, itemType, result.count(), result.average()))
                .defaultIfEmpty(new ReviewMetrics(itemId, itemType, 0, 0.0));
    }

    private EventReviewDocument toEventDocument(Review review) {
        return new EventReviewDocument(review.getId(), review.getItemId(), toUserDocument(review.getUser()),
                review.getRating(), review.getComment(), toReplyDocument(review.getReply()), review.getCreatedAt(),
                review.getStatus(), review.updatedAt(), toAssessmentDocument(review.getAiAssessment()),
                review.isAiRetained());
    }

    private ActivityReviewDocument toActivityDocument(Review review) {
        return new ActivityReviewDocument(review.getId(), review.getItemId(), toUserDocument(review.getUser()),
                review.getRating(), review.getComment(), toReplyDocument(review.getReply()), review.getCreatedAt(),
                review.getStatus(), review.updatedAt(), toAssessmentDocument(review.getAiAssessment()),
                review.isAiRetained());
    }

    private Review toEventDomain(EventReviewDocument document) {
        return Review.builder()
                .id(document.id())
                .itemId(document.itemId())
                .itemType(ReviewItemType.EVENT)
                .user(toUserDomain(document.user()))
                .rating(document.rating())
                .comment(document.comment())
                .reply(toReplyDomain(document.reply()))
                .createdAt(document.createdAt())
                .status(statusOf(document.status()))
                .updatedAt(updatedAtOf(document.updatedAt(), document.createdAt()))
                .aiAssessment(toAssessmentDomain(document.aiAssessment()))
                .aiRetained(document.aiRetained())
                .build();
    }

    private Review toActivityDomain(ActivityReviewDocument document) {
        return Review.builder()
                .id(document.id())
                .itemId(document.itemId())
                .itemType(ReviewItemType.ACTIVITY)
                .user(toUserDomain(document.user()))
                .rating(document.rating())
                .comment(document.comment())
                .reply(toReplyDomain(document.reply()))
                .createdAt(document.createdAt())
                .status(statusOf(document.status()))
                .updatedAt(updatedAtOf(document.updatedAt(), document.createdAt()))
                .aiAssessment(toAssessmentDomain(document.aiAssessment()))
                .aiRetained(document.aiRetained())
                .build();
    }

    /** Documents written before the status field existed are published reviews. */
    private static ReviewStatus statusOf(ReviewStatus status) {
        return status == null ? ReviewStatus.PUBLISHED : status;
    }

    private static Instant updatedAtOf(Instant updatedAt, Instant createdAt) {
        return updatedAt == null ? createdAt : updatedAt;
    }

    private ReviewUserDocument toUserDocument(ReviewUser user) {
        return user == null ? null : new ReviewUserDocument(user.id(), user.name());
    }

    private ReviewUser toUserDomain(ReviewUserDocument user) {
        return user == null ? null : new ReviewUser(user.id(), user.name());
    }

    private ReviewReplyDocument toReplyDocument(ReviewReply reply) {
        return reply == null ? null : new ReviewReplyDocument(reply.message(), reply.responderId(), reply.createdAt());
    }

    private ReviewReply toReplyDomain(ReviewReplyDocument reply) {
        return reply == null ? null : new ReviewReply(reply.message(), reply.responderId(), reply.createdAt());
    }

    private ReviewAiAssessmentDocument toAssessmentDocument(ReviewAiAssessment assessment) {
        return assessment == null ? null : new ReviewAiAssessmentDocument(
                assessment.status(), assessment.flagged(), assessment.score(), assessment.categories(),
                assessment.reason(), assessment.model(), assessment.checkedAt());
    }

    private ReviewAiAssessment toAssessmentDomain(ReviewAiAssessmentDocument assessment) {
        return assessment == null ? null : new ReviewAiAssessment(
                assessment.status(), assessment.flagged(), assessment.score(), assessment.categories(),
                assessment.reason(), assessment.model(), assessment.checkedAt());
    }
}
