package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo;

import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewReply;
import com.smxplore.activitiesevents.domain.model.review.ReviewUser;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document.ActivityReviewDocument;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document.EventReviewDocument;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document.ReviewReplyDocument;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document.ReviewUserDocument;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.repository.ActivityReviewMongoRepository;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.repository.EventReviewMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
            case EVENT -> eventReviews.findByItemIdOrderByCreatedAtDesc(itemId).map(this::toEventDomain);
            case ACTIVITY -> activityReviews.findByItemIdOrderByCreatedAtDesc(itemId).map(this::toActivityDomain);
        };
    }

    @Override
    public Mono<Boolean> existsByItemAndUser(UUID itemId, ReviewItemType itemType, UUID userId) {
        return switch (itemType) {
            case EVENT -> eventReviews.existsByItemAndUser(itemId, userId);
            case ACTIVITY -> activityReviews.existsByItemAndUser(itemId, userId);
        };
    }

    private EventReviewDocument toEventDocument(Review review) {
        return new EventReviewDocument(review.getId(), review.getItemId(), toUserDocument(review.getUser()),
                review.getRating(), review.getComment(), toReplyDocument(review.getReply()), review.getCreatedAt());
    }

    private ActivityReviewDocument toActivityDocument(Review review) {
        return new ActivityReviewDocument(review.getId(), review.getItemId(), toUserDocument(review.getUser()),
                review.getRating(), review.getComment(), toReplyDocument(review.getReply()), review.getCreatedAt());
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
                .build();
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
}
