package com.smxplore.activitiesevents.infrastructure.config;

import com.smxplore.activitiesevents.application.reviews.ConsultReviewsByItemUseCase;
import com.smxplore.activitiesevents.application.reviews.CreateReviewUseCase;
import com.smxplore.activitiesevents.application.reviews.RespondToReviewUseCase;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ConsultReviewsByItemUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.CreateReviewUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.RespondToReviewUseCasePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ReviewUseCaseConfiguration {

    @Bean
    public CreateReviewUseCasePort createReviewUseCase(ReviewRepositoryPort reviewRepository,
                                                       EventRepositoryPort eventRepository,
                                                       ActivityRepositoryPort activityRepository) {
        return new CreateReviewUseCase(reviewRepository, eventRepository, activityRepository);
    }

    @Bean
    public ConsultReviewsByItemUseCasePort consultReviewsByItemUseCase(ReviewRepositoryPort reviewRepository) {
        return new ConsultReviewsByItemUseCase(reviewRepository);
    }

    @Bean
    public RespondToReviewUseCasePort respondToReviewUseCase(ReviewRepositoryPort reviewRepository,
                                                             ActivityRepositoryPort activityRepository,
                                                             EventRepositoryPort eventRepository) {
        return new RespondToReviewUseCase(reviewRepository, activityRepository, eventRepository);
    }
}
