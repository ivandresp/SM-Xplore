package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewAlreadyExistsException;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewUser;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.CreateReviewUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Users can review an event/activity without having a reservation. */
@RequiredArgsConstructor
public class CreateReviewUseCase implements CreateReviewUseCasePort {

    private final ReviewRepositoryPort reviewRepo;
    private final EventRepositoryPort eventRepo;
    private final ActivityRepositoryPort activityRepo;

    @Override
    public Mono<Review> handle(UUID itemId, ReviewItemType itemType, UUID userId, String userName,
                               int rating, String comment) {
        var review = Review.builder()
                .itemId(itemId)
                .itemType(itemType)
                .user(new ReviewUser(userId, userName))
                .rating(rating)
                .comment(comment)
                .build();
        review.validate();

        return validateItemExists(itemId, itemType)
                .then(reviewRepo.existsByItemAndUser(itemId, itemType, userId))
                .flatMap(exists -> exists
                        ? Mono.error(new ReviewAlreadyExistsException(
                                "The user already reviewed this item."))
                        : reviewRepo.save(review));
    }

    private Mono<Void> validateItemExists(UUID itemId, ReviewItemType itemType) {
        return switch (itemType) {
            case EVENT -> eventRepo.findById(itemId)
                    .switchIfEmpty(Mono.error(new EventNotFoundException(itemId)))
                    .then();
            case ACTIVITY -> activityRepo.findById(itemId)
                    .switchIfEmpty(Mono.error(new ActivityNotFoundException(itemId)))
                    .then();
        };
    }
}
