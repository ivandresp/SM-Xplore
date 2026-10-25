package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.review.InvalidReviewException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewAlreadyRespondedException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewResponseNotAllowedException;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewReply;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.RespondToReviewUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Only the entity in charge may answer: the guide of the reviewed activity, or
 * an admin/destination manager for events (events belong to the destination).
 */
@RequiredArgsConstructor
public class RespondToReviewUseCase implements RespondToReviewUseCasePort {

    private static final String ADMIN = "ADMIN";
    private static final String DESTINATION_MANAGER = "DESTINATION_MANAGER";
    private static final int MESSAGE_MAX_LENGTH = 1000;

    private final ReviewRepositoryPort reviewRepo;
    private final ActivityRepositoryPort activityRepo;
    private final EventRepositoryPort eventRepo;

    @Override
    public Mono<Review> handle(UUID reviewId, UUID responderId, String role, String message) {
        if (message == null || message.isBlank()) {
            return Mono.error(new InvalidReviewException("Response message must not be blank."));
        }
        if (message.length() > MESSAGE_MAX_LENGTH) {
            return Mono.error(new InvalidReviewException(
                    "Response message must not exceed %d characters.".formatted(MESSAGE_MAX_LENGTH)));
        }
        return reviewRepo.findById(reviewId)
                .switchIfEmpty(Mono.error(new ReviewNotFoundException(reviewId)))
                .flatMap(review -> canRespond(review, responderId, role)
                        .then(Mono.defer(() -> {
                            if (review.getReply() != null) {
                                return Mono.error(new ReviewAlreadyRespondedException(
                                        "This review already has a response."));
                            }
                            return reviewRepo.save(review.withReply(
                                    new ReviewReply(message, responderId, Instant.now())));
                        })));
    }

    private Mono<Void> canRespond(Review review, UUID responderId, String role) {
        if (ADMIN.equals(role)) {
            return Mono.empty();
        }
        if (review.getItemType() == ReviewItemType.ACTIVITY && DESTINATION_MANAGER.equals(role)) {
            return Mono.empty();
        }
        if (review.getItemType() == ReviewItemType.EVENT) {
            return eventRepo.findById(review.getItemId())
                    .switchIfEmpty(Mono.error(new EventNotFoundException(review.getItemId())))
                    .flatMap(event -> DESTINATION_MANAGER.equals(role) && responderId.equals(event.getManagerId())
                            ? Mono.empty()
                            : Mono.error(new ReviewResponseNotAllowedException(
                                    "Only the destination manager in charge of the event can respond.")));
        }
        return activityRepo.findById(review.getItemId())
                .switchIfEmpty(Mono.error(new ActivityNotFoundException(review.getItemId())))
                .flatMap(activity -> activity.getGuideId() != null && activity.getGuideId().equals(responderId)
                        ? Mono.empty()
                        : Mono.error(new ReviewResponseNotAllowedException(
                                "Only the guide in charge of the activity can respond.")));
    }
}
