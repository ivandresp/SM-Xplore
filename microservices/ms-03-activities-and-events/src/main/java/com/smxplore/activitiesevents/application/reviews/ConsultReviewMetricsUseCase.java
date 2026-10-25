package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewMetrics;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ConsultReviewMetricsUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ConsultReviewMetricsUseCase implements ConsultReviewMetricsUseCasePort {

    private final ReviewRepositoryPort reviewRepo;
    private final EventRepositoryPort eventRepo;
    private final ActivityRepositoryPort activityRepo;

    @Override
    public Mono<ReviewMetrics> handle(UUID itemId, ReviewItemType itemType) {
        return validateItem(itemId, itemType)
                .then(reviewRepo.metrics(itemId, itemType));
    }

    private Mono<Void> validateItem(UUID itemId, ReviewItemType itemType) {
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
