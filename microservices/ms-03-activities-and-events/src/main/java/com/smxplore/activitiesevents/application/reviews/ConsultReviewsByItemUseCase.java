package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ConsultReviewsByItemUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RequiredArgsConstructor
public class ConsultReviewsByItemUseCase implements ConsultReviewsByItemUseCasePort {

    private final ReviewRepositoryPort reviewRepo;

    @Override
    public Flux<Review> handle(UUID itemId, ReviewItemType itemType) {
        return reviewRepo.findByItem(itemId, itemType).take(200);
    }
}
