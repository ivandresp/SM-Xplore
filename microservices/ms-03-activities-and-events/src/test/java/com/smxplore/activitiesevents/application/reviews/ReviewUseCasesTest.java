package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.exceptions.review.ReviewAlreadyExistsException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewAlreadyReportedException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewResponseNotAllowedException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewMetrics;
import com.smxplore.activitiesevents.domain.model.review.ReviewUser;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewReportRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ReviewUseCasesTest {

    @Mock
    private ReviewRepositoryPort reviewRepo;

    @Mock
    private ReviewReportRepositoryPort reportRepo;

    @Mock
    private EventRepositoryPort eventRepo;

    @Mock
    private ActivityRepositoryPort activityRepo;

    private CreateReviewUseCase createReview;
    private RespondToReviewUseCase respondToReview;

    @BeforeEach
    void setUp() {
        createReview = new CreateReviewUseCase(reviewRepo, eventRepo, activityRepo);
        respondToReview = new RespondToReviewUseCase(reviewRepo, activityRepo, eventRepo);
    }

    @Test
    void createsReviewWithoutReservationAndKeepsNestedUser() {
        var activity = activity(UUID.randomUUID());
        var userId = UUID.randomUUID();
        given(activityRepo.findById(activity.getId())).willReturn(Mono.just(activity));
        given(reviewRepo.existsByItemAndUser(activity.getId(), ReviewItemType.ACTIVITY, userId))
                .willReturn(Mono.just(false));
        given(reviewRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(createReview.handle(activity.getId(), ReviewItemType.ACTIVITY, userId, "Ana", 5, "Great"))
                .assertNext(review -> {
                    assertThat(review.getUser().id()).isEqualTo(userId);
                    assertThat(review.getUser().name()).isEqualTo("Ana");
                    assertThat(review.getRating()).isEqualTo(5);
                })
                .verifyComplete();
    }

    @Test
    void rejectsSecondReviewFromSameUser() {
        var activity = activity(UUID.randomUUID());
        var userId = UUID.randomUUID();
        given(activityRepo.findById(activity.getId())).willReturn(Mono.just(activity));
        given(reviewRepo.existsByItemAndUser(activity.getId(), ReviewItemType.ACTIVITY, userId))
                .willReturn(Mono.just(true));

        StepVerifier.create(createReview.handle(activity.getId(), ReviewItemType.ACTIVITY, userId, "Ana", 5, "Great"))
                .expectError(ReviewAlreadyExistsException.class)
                .verify();
    }

    @Test
    void guideInChargeCanRespondToActivityReview() {
        var guideId = UUID.randomUUID();
        var review = review(ReviewItemType.ACTIVITY, UUID.randomUUID());
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(activityRepo.findById(review.getItemId())).willReturn(Mono.just(activity(guideId)));
        given(reviewRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(respondToReview.handle(review.getId(), guideId, "GUIDE", "Thanks!"))
                .assertNext(updated -> {
                    assertThat(updated.getReply()).isNotNull();
                    assertThat(updated.getReply().message()).isEqualTo("Thanks!");
                    assertThat(updated.getReply().responderId()).isEqualTo(guideId);
                })
                .verifyComplete();
    }

    @Test
    void anotherGuideCannotRespondToActivityReview() {
        var review = review(ReviewItemType.ACTIVITY, UUID.randomUUID());
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(activityRepo.findById(review.getItemId())).willReturn(Mono.just(activity(UUID.randomUUID())));

        StepVerifier.create(respondToReview.handle(review.getId(), UUID.randomUUID(), "GUIDE", "Thanks!"))
                .expectError(ReviewResponseNotAllowedException.class)
                .verify();
    }

    @Test
    void destinationManagerCannotRespondToActivityReview() {
        var review = review(ReviewItemType.ACTIVITY, UUID.randomUUID());
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(activityRepo.findById(review.getItemId())).willReturn(Mono.just(activity(UUID.randomUUID())));

        StepVerifier.create(respondToReview.handle(review.getId(), UUID.randomUUID(), "DESTINATION_MANAGER", "Thanks!"))
                .expectError(ReviewResponseNotAllowedException.class)
                .verify();
    }

    @Test
    void staffCanRespondToEventReview() {
        var review = review(ReviewItemType.EVENT, UUID.randomUUID());
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(reviewRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(respondToReview.handle(review.getId(), UUID.randomUUID(), "ADMIN", "Thanks!"))
                .assertNext(updated -> assertThat(updated.getReply()).isNotNull())
                .verifyComplete();
    }

    @Test
    void guideCannotRespondToEventReview() {
        var review = review(ReviewItemType.EVENT, UUID.randomUUID());
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(eventRepo.findById(review.getItemId())).willReturn(Mono.just(event(UUID.randomUUID())));

        StepVerifier.create(respondToReview.handle(review.getId(), UUID.randomUUID(), "GUIDE", "Thanks!"))
                .expectError(ReviewResponseNotAllowedException.class)
                .verify();
    }

    @Test
    void destinationManagerInChargeCanRespondToEventReview() {
        var managerId = UUID.randomUUID();
        var review = review(ReviewItemType.EVENT, UUID.randomUUID());
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(eventRepo.findById(review.getItemId())).willReturn(Mono.just(event(managerId)));
        given(reviewRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(respondToReview.handle(review.getId(), managerId, "DESTINATION_MANAGER", "Thanks!"))
                .assertNext(updated -> assertThat(updated.getReply()).isNotNull())
                .verifyComplete();
    }

    @Test
    void anotherDestinationManagerCannotRespondToEventReview() {
        var review = review(ReviewItemType.EVENT, UUID.randomUUID());
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(eventRepo.findById(review.getItemId())).willReturn(Mono.just(event(UUID.randomUUID())));

        StepVerifier.create(respondToReview.handle(review.getId(), UUID.randomUUID(), "DESTINATION_MANAGER", "Thanks!"))
                .expectError(ReviewResponseNotAllowedException.class)
                .verify();
    }

    @Test
    void reportsReviewOncePerUser() {
        var review = review(ReviewItemType.ACTIVITY, UUID.randomUUID());
        var reporterId = UUID.randomUUID();
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(reportRepo.existsByReviewAndReporter(review.getId(), reporterId)).willReturn(Mono.just(false));
        given(reportRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var reporter = new ReportReviewUseCase(reviewRepo, reportRepo);

        StepVerifier.create(reporter.handle(review.getId(), reporterId, "Spam"))
                .assertNext(found -> assertThat(found.getId()).isEqualTo(review.getId()))
                .verifyComplete();
    }

    @Test
    void rejectsSecondReportFromSameUser() {
        var review = review(ReviewItemType.ACTIVITY, UUID.randomUUID());
        var reporterId = UUID.randomUUID();
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(reportRepo.existsByReviewAndReporter(review.getId(), reporterId)).willReturn(Mono.just(true));

        var reporter = new ReportReviewUseCase(reviewRepo, reportRepo);

        StepVerifier.create(reporter.handle(review.getId(), reporterId, "Spam"))
                .expectError(ReviewAlreadyReportedException.class)
                .verify();
    }

    @Test
    void computesReviewMetrics() {
        var itemId = UUID.randomUUID();
        var first = review(ReviewItemType.EVENT, itemId).toBuilder().rating(4).build();
        var second = review(ReviewItemType.EVENT, itemId).toBuilder().rating(5).build();
        given(eventRepo.findById(itemId)).willReturn(Mono.just(event(UUID.randomUUID())));
        given(reviewRepo.findByItem(itemId, ReviewItemType.EVENT)).willReturn(reactor.core.publisher.Flux.just(first, second));

        StepVerifier.create(new ConsultReviewMetricsUseCase(reviewRepo, eventRepo, activityRepo)
                        .handle(itemId, ReviewItemType.EVENT))
                .assertNext(metrics -> {
                    assertThat(metrics.count()).isEqualTo(2);
                    assertThat(metrics.averageRating()).isEqualTo(4.5);
                })
                .verifyComplete();
    }

    private Review review(ReviewItemType itemType, UUID itemId) {
        return Review.builder()
                .itemId(itemId)
                .itemType(itemType)
                .user(new ReviewUser(UUID.randomUUID(), "Ana"))
                .rating(5)
                .comment("Great")
                .build();
    }

    private Activity activity(UUID guideId) {
        var startsAt = Instant.now().plusSeconds(3_600);
        return Activity.builder()
                .siteId(UUID.randomUUID())
                .guideId(guideId)
                .name("Snorkel")
                .category("Water")
                .status(ActivityStatus.PUBLISHED)
                .price(new BigDecimal("80000.00"))
                .maxCapacity(10)
                .durationMinutes(60)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(3_600))
                .build();
    }

    private Event event(UUID managerId) {
        var startsAt = Instant.now().plusSeconds(3_600);
        return Event.builder()
                .siteId(UUID.randomUUID())
                .managerId(managerId)
                .name("Kayak")
                .category("Nautical")
                .status(EventStatus.PUBLISHED)
                .price(new BigDecimal("150000.00"))
                .maxCapacity(20)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(3_600))
                .build();
    }
}
