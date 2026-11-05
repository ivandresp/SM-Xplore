package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.exceptions.review.InvalidReviewException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewModerationNotAllowedException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewNotFoundException;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ModerateReviewUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Administrative moderation: publishes, hides, deletes or returns a review to the pending queue.
 * Every effective change is published as {@code reviews.review.changed} so consumers update their
 * projections.
 */
@RequiredArgsConstructor
public class ModerateReviewUseCase implements ModerateReviewUseCasePort {

    private static final String ADMIN = "ADMIN";

    private final ReviewRepositoryPort reviewRepo;
    private final ReviewEventPublisher publisher;

    @Override
    public Mono<Review> handle(UUID reviewId, ReviewStatus status, UUID moderatorId, String role) {
        if (!ADMIN.equals(role)) {
            return Mono.error(new ReviewModerationNotAllowedException(
                    "Only an administrator can moderate reviews."));
        }
        if (status == null) {
            return Mono.error(new InvalidReviewException("The target review status is required."));
        }
        return reviewRepo.findById(reviewId)
                .switchIfEmpty(Mono.error(new ReviewNotFoundException(reviewId)))
                .flatMap(review -> {
                    if (review.getStatus() == status) {
                        return Mono.just(review);
                    }
                    return reviewRepo.save(review.withStatus(status))
                            .flatMap(publisher::publish);
                });
    }
}
