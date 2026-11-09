package com.smxplore.activitiesevents.infrastructure.config;

import com.smxplore.activitiesevents.application.reviews.ConsultGuideReviewReportsUseCase;
import com.smxplore.activitiesevents.application.reviews.ConsultManagerReviewReportsUseCase;
import com.smxplore.activitiesevents.application.reviews.ConsultReviewMetricsUseCase;
import com.smxplore.activitiesevents.application.reviews.ConsultReviewReportsUseCase;
import com.smxplore.activitiesevents.application.reviews.ConsultReviewsByItemUseCase;
import com.smxplore.activitiesevents.application.reviews.ConsultReviewsForModerationUseCase;
import com.smxplore.activitiesevents.application.reviews.CreateReviewUseCase;
import com.smxplore.activitiesevents.application.reviews.ModerateReviewUseCase;
import com.smxplore.activitiesevents.application.reviews.ReportReviewUseCase;
import com.smxplore.activitiesevents.application.reviews.ResolveReviewReportUseCase;
import com.smxplore.activitiesevents.application.reviews.RespondToReviewUseCase;
import com.smxplore.activitiesevents.application.reviews.ReviewEventPublisher;
import com.smxplore.activitiesevents.domain.model.review.ReviewAiPolicy;
import com.smxplore.activitiesevents.domain.ports.ai.ReviewAiFilter;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewReportRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ConsultReviewMetricsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ConsultReviewReportsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ConsultReviewsByItemUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ConsultReviewsForModerationUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.CreateReviewUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ModerateReviewUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ReportReviewUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ResolveReviewReportUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.RespondToReviewUseCasePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ReviewUseCaseConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ReviewAiPolicy reviewAiPolicy(ReviewAiProperties properties) {
        return new ReviewAiPolicy(properties.threshold(), properties.action(), properties.failOpen());
    }

    @Bean
    public CreateReviewUseCasePort createReviewUseCase(ReviewRepositoryPort reviewRepository,
                                                       EventRepositoryPort eventRepository,
                                                       ActivityRepositoryPort activityRepository,
                                                       ReservationRepositoryPort reservationRepository,
                                                       ReviewAiFilter aiFilter,
                                                       ReviewAiPolicy aiPolicy,
                                                       ReviewEventPublisher publisher) {
        return new CreateReviewUseCase(reviewRepository, eventRepository, activityRepository,
                reservationRepository, aiFilter, aiPolicy, publisher);
    }

    @Bean
    public ConsultReviewsByItemUseCasePort consultReviewsByItemUseCase(ReviewRepositoryPort reviewRepository) {
        return new ConsultReviewsByItemUseCase(reviewRepository);
    }

    @Bean
    public RespondToReviewUseCasePort respondToReviewUseCase(ReviewRepositoryPort reviewRepository,
                                                             ActivityRepositoryPort activityRepository,
                                                             EventRepositoryPort eventRepository,
                                                             SiteCatalogPort siteCatalog,
                                                             ReviewEventPublisher publisher) {
        return new RespondToReviewUseCase(reviewRepository, activityRepository, eventRepository,
                siteCatalog, publisher);
    }

    @Bean
    public ReportReviewUseCasePort reportReviewUseCase(ReviewRepositoryPort reviewRepository,
                                                       ReviewReportRepositoryPort reportRepository) {
        return new ReportReviewUseCase(reviewRepository, reportRepository);
    }

    @Bean
    public ConsultReviewMetricsUseCasePort consultReviewMetricsUseCase(ReviewRepositoryPort reviewRepository,
                                                                       EventRepositoryPort eventRepository,
                                                                       ActivityRepositoryPort activityRepository) {
        return new ConsultReviewMetricsUseCase(reviewRepository, eventRepository, activityRepository);
    }

    @Bean
    public ConsultReviewReportsUseCasePort consultReviewReportsUseCase(ReviewReportRepositoryPort reportRepository) {
        return new ConsultReviewReportsUseCase(reportRepository);
    }

    @Bean
    public ConsultManagerReviewReportsUseCase consultManagerReviewReportsUseCase(
            SiteCatalogPort siteCatalog,
            EventRepositoryPort eventRepository,
            ReviewRepositoryPort reviewRepository,
            ReviewReportRepositoryPort reportRepository) {
        return new ConsultManagerReviewReportsUseCase(siteCatalog, eventRepository, reviewRepository,
                reportRepository);
    }

    @Bean
    public ConsultGuideReviewReportsUseCase consultGuideReviewReportsUseCase(
            ActivityRepositoryPort activityRepository,
            ReviewRepositoryPort reviewRepository,
            ReviewReportRepositoryPort reportRepository) {
        return new ConsultGuideReviewReportsUseCase(activityRepository, reviewRepository,
                reportRepository);
    }

    @Bean
    public ResolveReviewReportUseCasePort resolveReviewReportUseCase(ReviewReportRepositoryPort reportRepository,
                                                                     ReviewRepositoryPort reviewRepository,
                                                                     EventRepositoryPort eventRepository,
                                                                     ActivityRepositoryPort activityRepository,
                                                                     ReviewEventPublisher publisher) {
        return new ResolveReviewReportUseCase(reportRepository, reviewRepository, eventRepository,
                activityRepository, publisher);
    }

    @Bean
    public ModerateReviewUseCasePort moderateReviewUseCase(ReviewRepositoryPort reviewRepository,
                                                           ReviewEventPublisher publisher) {
        return new ModerateReviewUseCase(reviewRepository, publisher);
    }

    @Bean
    public ConsultReviewsForModerationUseCasePort consultReviewsForModerationUseCase(
            ReviewRepositoryPort reviewRepository) {
        return new ConsultReviewsForModerationUseCase(reviewRepository);
    }
}
