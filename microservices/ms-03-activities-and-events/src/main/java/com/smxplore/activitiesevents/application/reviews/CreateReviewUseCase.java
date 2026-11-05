package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.review.InvalidReviewException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewAlreadyExistsException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewFilterUnavailableException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewAiAssessment;
import com.smxplore.activitiesevents.domain.model.review.ReviewAiPolicy;
import com.smxplore.activitiesevents.domain.model.review.ReviewAiRequest;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewUser;
import com.smxplore.activitiesevents.domain.ports.ai.ReviewAiFilter;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.CreateReviewUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/** Users can review a published event/activity without having a reservation. */
@RequiredArgsConstructor
public class CreateReviewUseCase implements CreateReviewUseCasePort {

    private final ReviewRepositoryPort reviewRepo;
    private final EventRepositoryPort eventRepo;
    private final ActivityRepositoryPort activityRepo;
    private final ReviewAiFilter aiFilter;
    private final ReviewAiPolicy aiPolicy;
    private final ReviewEventPublisher publisher;

    @Override
    public Mono<Review> handle(UUID itemId, ReviewItemType itemType, UUID userId, String userName,
                               int rating, String comment) {
        var now = Instant.now();
        var review = Review.builder()
                .itemId(itemId)
                .itemType(itemType)
                .user(new ReviewUser(userId, userName))
                .rating(rating)
                .comment(comment)
                .createdAt(now)
                .updatedAt(now)
                .build();
        review.validate();

        return validateItem(itemId, itemType, userId)
                .then(reviewRepo.existsByItemAndUser(itemId, itemType, userId))
                .flatMap(exists -> exists
                        ? Mono.error(new ReviewAlreadyExistsException(
                                "The user already reviewed this item."))
                        : assess(review, now))
                .flatMap(reviewRepo::save)
                .flatMap(publisher::publish);
    }

    /**
     * Runs the automatic filter unless the comment is empty. An outage follows the configured
     * policy: fail-open leaves the review published, fail-closed returns 503.
     */
    private Mono<Review> assess(Review review, Instant now) {
        if (review.getComment() == null || review.getComment().isBlank()) {
            return Mono.just(review.withAiAssessment(ReviewAiAssessment.skipped(now), aiPolicy, now));
        }
        var request = new ReviewAiRequest(review.getItemType().name(), review.getRating(),
                review.getComment());
        return aiFilter.assess(request)
                .onErrorResume(error -> Mono.just(ReviewAiAssessment.unavailable(now)))
                .flatMap(assessment -> {
                    if (aiPolicy.shouldRejectOnFailure(assessment)) {
                        return Mono.error(new ReviewFilterUnavailableException());
                    }
                    return Mono.just(review.withAiAssessment(assessment, aiPolicy, now));
                });
    }

    private Mono<Void> validateItem(UUID itemId, ReviewItemType itemType, UUID userId) {
        return switch (itemType) {
            case EVENT -> eventRepo.findById(itemId)
                    .switchIfEmpty(Mono.error(new EventNotFoundException(itemId)))
                    .flatMap(event -> validateEvent(event, userId));
            case ACTIVITY -> activityRepo.findById(itemId)
                    .switchIfEmpty(Mono.error(new ActivityNotFoundException(itemId)))
                    .flatMap(activity -> validateActivity(activity, userId));
        };
    }

    private Mono<Void> validateEvent(Event event, UUID userId) {
        if (event.getStatus() != EventStatus.PUBLISHED) {
            return Mono.error(new InvalidReviewException("The event is not published."));
        }
        if (event.getEndsAt() == null || !event.getEndsAt().isAfter(Instant.now())) {
            return Mono.error(new InvalidReviewException("The event has already ended."));
        }
        if (event.getManagerId() != null && event.getManagerId().equals(userId)) {
            return Mono.error(new InvalidReviewException("The manager of the event cannot review it."));
        }
        return Mono.empty();
    }

    private Mono<Void> validateActivity(Activity activity, UUID userId) {
        if (activity.getStatus() != ActivityStatus.PUBLISHED) {
            return Mono.error(new InvalidReviewException("The activity is not published."));
        }
        if (activity.getEndsAt() == null || !activity.getEndsAt().isAfter(Instant.now())) {
            return Mono.error(new InvalidReviewException("The activity has already ended."));
        }
        if (activity.getGuideId() != null && activity.getGuideId().equals(userId)) {
            return Mono.error(new InvalidReviewException("The guide of the activity cannot review it."));
        }
        return Mono.empty();
    }
}
