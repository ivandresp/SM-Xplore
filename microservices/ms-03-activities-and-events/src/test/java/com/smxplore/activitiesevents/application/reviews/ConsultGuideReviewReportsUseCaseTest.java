package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewReportRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Bandeja del guia: solo los reportes de las resenas de sus propias actividades, acotados por el
 * limit antes de cruzar Mongo.
 */
@ExtendWith(MockitoExtension.class)
class ConsultGuideReviewReportsUseCaseTest {

    @Mock
    private ActivityRepositoryPort activityRepo;

    @Mock
    private ReviewRepositoryPort reviewRepo;

    @Mock
    private ReviewReportRepositoryPort reportRepo;

    private ConsultGuideReviewReportsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConsultGuideReviewReportsUseCase(activityRepo, reviewRepo, reportRepo);
    }

    @Test
    void listsTheReportsOfItsOwnActivities() {
        var guideId = UUID.randomUUID();
        var activity = activity(guideId);
        var review = review(activity.getId());
        var report = ReviewReport.pending(review.getId(), UUID.randomUUID(), "Spam");
        given(activityRepo.findAllByGuideId(guideId)).willReturn(Flux.just(activity));
        given(reviewRepo.findByItemIds(anyCollection(), eq(ReviewItemType.ACTIVITY)))
                .willReturn(Flux.just(review));
        given(reportRepo.findByStatusAndReviewIds(ReviewReportStatus.PENDING,
                java.util.List.of(review.getId()), 25))
                .willReturn(Flux.just(report));

        StepVerifier.create(useCase.handle(guideId, ReviewReportStatus.PENDING, 25))
                .assertNext(found -> assertThat(found.reviewId()).isEqualTo(review.getId()))
                .verifyComplete();

        then(reportRepo).should().findByStatusAndReviewIds(ReviewReportStatus.PENDING,
                java.util.List.of(review.getId()), 25);
    }

    @Test
    void aGuideWithoutActivitiesGetsAnEmptyInbox() {
        var guideId = UUID.randomUUID();
        given(activityRepo.findAllByGuideId(guideId)).willReturn(Flux.empty());

        StepVerifier.create(useCase.handle(guideId, ReviewReportStatus.PENDING, 20))
                .verifyComplete();

        then(reviewRepo).should(never()).findByItemIds(anyCollection(), any());
    }

    @Test
    void activitiesWithoutReviewsDoNotQueryReports() {
        var guideId = UUID.randomUUID();
        var activity = activity(guideId);
        given(activityRepo.findAllByGuideId(guideId)).willReturn(Flux.just(activity));
        given(reviewRepo.findByItemIds(anyCollection(), eq(ReviewItemType.ACTIVITY)))
                .willReturn(Flux.empty());

        StepVerifier.create(useCase.handle(guideId, ReviewReportStatus.PENDING, 20))
                .verifyComplete();

        then(reportRepo).shouldHaveNoInteractions();
    }

    @Test
    void aMissingGuideIdIsRejectedWithoutQueries() {
        StepVerifier.create(useCase.handle(null, ReviewReportStatus.PENDING, 20)).verifyComplete();

        then(activityRepo).shouldHaveNoInteractions();
    }

    private static Activity activity(UUID guideId) {
        return Activity.builder()
                .id(UUID.randomUUID())
                .siteId(UUID.randomUUID())
                .guideId(guideId)
                .name("Snorkel")
                .category("AVENTURA")
                .price(new BigDecimal("25.00"))
                .maxCapacity(10)
                .durationMinutes(120)
                .startsAt(Instant.now().plusSeconds(3600))
                .endsAt(Instant.now().plusSeconds(10800))
                .status(ActivityStatus.PUBLISHED)
                .build();
    }

    private static Review review(UUID itemId) {
        return Review.builder()
                .id(UUID.randomUUID())
                .itemId(itemId)
                .itemType(ReviewItemType.ACTIVITY)
                .rating(1)
                .comment("Comentario")
                .status(ReviewStatus.PUBLISHED)
                .createdAt(Instant.now())
                .build();
    }
}
