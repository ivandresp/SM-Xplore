package com.smxplore.activitiesevents.application.reviews;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smxplore.activitiesevents.application.outbox.ReviewEventFactory;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewAlreadyExistsException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewAlreadyReportedException;
import com.smxplore.activitiesevents.domain.exceptions.review.InvalidReviewException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewFilterUnavailableException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewModerationNotAllowedException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewReportNotAllowedException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewResponseNotAllowedException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewAiAction;
import com.smxplore.activitiesevents.domain.model.review.ReviewAiAssessment;
import com.smxplore.activitiesevents.domain.model.review.ReviewAiPolicy;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewMetrics;
import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;
import com.smxplore.activitiesevents.domain.model.review.ReviewUser;
import com.smxplore.activitiesevents.domain.ports.ai.ReviewAiFilter;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewReportRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

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

    @Mock
    private SiteCatalogPort siteCatalog;

    @Mock
    private OutboxPort outbox;

    @Mock
    private ReviewAiFilter aiFilter;

    private ReviewEventPublisher publisher;
    private ReviewAiPolicy aiPolicy;
    private CreateReviewUseCase createReview;
    private RespondToReviewUseCase respondToReview;

    @BeforeEach
    void setUp() {
        aiPolicy = new ReviewAiPolicy(0.7, ReviewAiAction.PENDING_REVIEW, true);
        publisher = new ReviewEventPublisher(outbox,
                new ReviewEventFactory(new ObjectMapper().findAndRegisterModules()));
        createReview = new CreateReviewUseCase(reviewRepo, eventRepo, activityRepo, aiFilter, aiPolicy,
                publisher);
        respondToReview = new RespondToReviewUseCase(reviewRepo, activityRepo, eventRepo, siteCatalog,
                publisher);
        lenient().when(outbox.enqueue(any())).thenReturn(Mono.empty());
    }

    @Test
    void createsReviewWithoutReservationAndKeepsNestedUser() {
        var activity = activity(UUID.randomUUID());
        var userId = UUID.randomUUID();
        given(activityRepo.findById(activity.getId())).willReturn(Mono.just(activity));
        given(reviewRepo.existsByItemAndUser(activity.getId(), ReviewItemType.ACTIVITY, userId))
                .willReturn(Mono.just(false));
        given(aiFilter.assess(any())).willReturn(Mono.just(
                ReviewAiAssessment.skipped(Instant.now())));
        given(reviewRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(createReview.handle(activity.getId(), ReviewItemType.ACTIVITY, userId, "Ana", 5, "Great"))
                .assertNext(review -> {
                    assertThat(review.getUser().id()).isEqualTo(userId);
                    assertThat(review.getUser().name()).isEqualTo("Ana");
                    assertThat(review.getRating()).isEqualTo(5);
                    assertThat(review.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
                    assertThat(review.isAiRetained()).isFalse();
                })
                .verifyComplete();

        then(outbox).should().enqueue(org.mockito.ArgumentMatchers.argThat(
                event -> "reviews.review.changed".equals(event.eventType())));
    }

    @Test
    void keepsTheReviewWhenTheEventCannotBeEnqueued() {
        var activity = activity(UUID.randomUUID());
        var userId = UUID.randomUUID();
        given(activityRepo.findById(activity.getId())).willReturn(Mono.just(activity));
        given(reviewRepo.existsByItemAndUser(activity.getId(), ReviewItemType.ACTIVITY, userId))
                .willReturn(Mono.just(false));
        given(aiFilter.assess(any())).willReturn(Mono.just(
                ReviewAiAssessment.skipped(Instant.now())));
        given(reviewRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        given(outbox.enqueue(any())).willReturn(Mono.error(new IllegalStateException("outbox down")));

        StepVerifier.create(createReview.handle(activity.getId(), ReviewItemType.ACTIVITY, userId, "Ana", 5, "Great"))
                .assertNext(review -> assertThat(review.getRating()).isEqualTo(5))
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
    void retainsAFlaggedReviewInTheModerationQueue() {
        var activity = activity(UUID.randomUUID());
        var userId = UUID.randomUUID();
        given(activityRepo.findById(activity.getId())).willReturn(Mono.just(activity));
        given(reviewRepo.existsByItemAndUser(activity.getId(), ReviewItemType.ACTIVITY, userId))
                .willReturn(Mono.just(false));
        given(aiFilter.assess(any())).willReturn(Mono.just(ReviewAiAssessment.assessed(
                true, 0.9, List.of("SPAM"), "spam", "littlellm", Instant.now())));
        given(reviewRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(createReview.handle(activity.getId(), ReviewItemType.ACTIVITY, userId, "Ana", 5, "Great"))
                .assertNext(review -> {
                    assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW);
                    assertThat(review.isAiRetained()).isTrue();
                    assertThat(review.getAiAssessment().flagged()).isTrue();
                })
                .verifyComplete();

        then(outbox).should().enqueue(org.mockito.ArgumentMatchers.argThat(
                event -> event.payload().contains("\"status\":\"PENDING_REVIEW\"")));
    }

    @Test
    void hidesAFlaggedReviewWhenThePolicySaysSo() {
        var activity = activity(UUID.randomUUID());
        var userId = UUID.randomUUID();
        var hidePolicy = new ReviewAiPolicy(0.7, ReviewAiAction.HIDE, true);
        var useCase = new CreateReviewUseCase(reviewRepo, eventRepo, activityRepo, aiFilter, hidePolicy,
                publisher);
        given(activityRepo.findById(activity.getId())).willReturn(Mono.just(activity));
        given(reviewRepo.existsByItemAndUser(activity.getId(), ReviewItemType.ACTIVITY, userId))
                .willReturn(Mono.just(false));
        given(aiFilter.assess(any())).willReturn(Mono.just(ReviewAiAssessment.assessed(
                true, 0.9, List.of("SPAM"), "spam", "littlellm", Instant.now())));
        given(reviewRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.handle(activity.getId(), ReviewItemType.ACTIVITY, userId, "Ana", 5, "Great"))
                .assertNext(review -> assertThat(review.getStatus()).isEqualTo(ReviewStatus.HIDDEN))
                .verifyComplete();
    }

    @Test
    void rejectsCreationWhenTheFilterFailsAndThePolicyIsClosed() {
        var activity = activity(UUID.randomUUID());
        var userId = UUID.randomUUID();
        var closedPolicy = new ReviewAiPolicy(0.7, ReviewAiAction.PENDING_REVIEW, false);
        var useCase = new CreateReviewUseCase(reviewRepo, eventRepo, activityRepo, aiFilter, closedPolicy,
                publisher);
        given(activityRepo.findById(activity.getId())).willReturn(Mono.just(activity));
        given(reviewRepo.existsByItemAndUser(activity.getId(), ReviewItemType.ACTIVITY, userId))
                .willReturn(Mono.just(false));
        given(aiFilter.assess(any())).willReturn(Mono.just(ReviewAiAssessment.unavailable(Instant.now())));

        StepVerifier.create(useCase.handle(activity.getId(), ReviewItemType.ACTIVITY, userId, "Ana", 5, "Great"))
                .expectError(ReviewFilterUnavailableException.class)
                .verify();
    }

    @Test
    void guideInChargeCanRespondToActivityReview() {
        var guideId = UUID.randomUUID();
        var review = review(ReviewItemType.ACTIVITY, UUID.randomUUID());
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(activityRepo.findById(review.getItemId())).willReturn(Mono.just(activity(guideId)));
        given(reviewRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(respondToReview.handle(review.getId(), guideId, "GUIDE", "Thanks!", "token"))
                .assertNext(updated -> {
                    assertThat(updated.getReply()).isNotNull();
                    assertThat(updated.getReply().message()).isEqualTo("Thanks!");
                    assertThat(updated.getReply().responderId()).isEqualTo(guideId);
                })
                .verifyComplete();

        then(outbox).should().enqueue(org.mockito.ArgumentMatchers.argThat(
                event -> event.payload().contains("\"reviewId\":\"" + review.getId() + "\"")));
    }

    @Test
    void anotherGuideCannotRespondToActivityReview() {
        var review = review(ReviewItemType.ACTIVITY, UUID.randomUUID());
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(activityRepo.findById(review.getItemId())).willReturn(Mono.just(activity(UUID.randomUUID())));

        StepVerifier.create(respondToReview.handle(review.getId(), UUID.randomUUID(), "GUIDE", "Thanks!", "token"))
                .expectError(ReviewResponseNotAllowedException.class)
                .verify();
    }

    @Test
    void destinationManagerCannotRespondToActivityReview() {
        var review = review(ReviewItemType.ACTIVITY, UUID.randomUUID());
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(activityRepo.findById(review.getItemId())).willReturn(Mono.just(activity(UUID.randomUUID())));

        StepVerifier.create(respondToReview.handle(review.getId(), UUID.randomUUID(), "DESTINATION_MANAGER", "Thanks!", "token"))
                .expectError(ReviewResponseNotAllowedException.class)
                .verify();
    }

    @Test
    void staffCanRespondToEventReview() {
        var review = review(ReviewItemType.EVENT, UUID.randomUUID());
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(reviewRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(respondToReview.handle(review.getId(), UUID.randomUUID(), "ADMIN", "Thanks!", "token"))
                .assertNext(updated -> assertThat(updated.getReply()).isNotNull())
                .verifyComplete();
    }

    @Test
    void guideCannotRespondToEventReview() {
        var review = review(ReviewItemType.EVENT, UUID.randomUUID());
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(eventRepo.findById(review.getItemId())).willReturn(Mono.just(event(UUID.randomUUID())));
        given(siteCatalog.findById(any(), any())).willReturn(Mono.empty());

        StepVerifier.create(respondToReview.handle(review.getId(), UUID.randomUUID(), "GUIDE", "Thanks!", "token"))
                .expectError(ReviewResponseNotAllowedException.class)
                .verify();
    }

    @Test
    void destinationManagerInChargeCanRespondToEventReview() {
        var managerId = UUID.randomUUID();
        var review = review(ReviewItemType.EVENT, UUID.randomUUID());
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(eventRepo.findById(review.getItemId())).willReturn(Mono.just(event(managerId)));
        given(siteCatalog.findById(any(), any())).willReturn(Mono.empty());
        given(reviewRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(respondToReview.handle(review.getId(), managerId, "DESTINATION_MANAGER", "Thanks!", "token"))
                .assertNext(updated -> assertThat(updated.getReply()).isNotNull())
                .verifyComplete();
    }

    @Test
    void anotherDestinationManagerCannotRespondToEventReview() {
        var review = review(ReviewItemType.EVENT, UUID.randomUUID());
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(eventRepo.findById(review.getItemId())).willReturn(Mono.just(event(UUID.randomUUID())));
        given(siteCatalog.findById(any(), any())).willReturn(Mono.empty());

        StepVerifier.create(respondToReview.handle(review.getId(), UUID.randomUUID(), "DESTINATION_MANAGER", "Thanks!", "token"))
                .expectError(ReviewResponseNotAllowedException.class)
                .verify();
    }

    @Test
    void revalidatesTheManagerAgainstTheCurrentSite() {
        var formerManager = UUID.randomUUID();
        var review = review(ReviewItemType.EVENT, UUID.randomUUID());
        var event = event(formerManager);
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(eventRepo.findById(review.getItemId())).willReturn(Mono.just(event));
        given(siteCatalog.findById(event.getSiteId(), "token"))
                .willReturn(Mono.just(new SiteCatalogPort.SiteSnapshot(
                        event.getSiteId(), UUID.randomUUID(), "Sitio")));

        StepVerifier.create(respondToReview.handle(review.getId(), formerManager,
                        "DESTINATION_MANAGER", "Thanks!", "token"))
                .expectError(ReviewResponseNotAllowedException.class)
                .verify();
    }

    @Test
    void allowsTheManagerThatOwnsTheSiteToday() {
        var newManager = UUID.randomUUID();
        var review = review(ReviewItemType.EVENT, UUID.randomUUID());
        var event = event(UUID.randomUUID());
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(eventRepo.findById(review.getItemId())).willReturn(Mono.just(event));
        given(siteCatalog.findById(event.getSiteId(), "token"))
                .willReturn(Mono.just(new SiteCatalogPort.SiteSnapshot(
                        event.getSiteId(), newManager, "Sitio")));
        given(reviewRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(respondToReview.handle(review.getId(), newManager,
                        "DESTINATION_MANAGER", "Thanks!", "token"))
                .assertNext(updated -> assertThat(updated.getReply()).isNotNull())
                .verifyComplete();
    }

    @Test
    void fallsBackToTheSnapshotWhenMs04Fails() {
        var managerId = UUID.randomUUID();
        var review = review(ReviewItemType.EVENT, UUID.randomUUID());
        var event = event(managerId);
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(eventRepo.findById(review.getItemId())).willReturn(Mono.just(event));
        given(siteCatalog.findById(event.getSiteId(), "token"))
                .willReturn(Mono.error(new IllegalStateException("ms-04 down")));
        given(reviewRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(respondToReview.handle(review.getId(), managerId,
                        "DESTINATION_MANAGER", "Thanks!", "token"))
                .assertNext(updated -> assertThat(updated.getReply()).isNotNull())
                .verifyComplete();
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
    void listsPendingReports() {
        var report = ReviewReport.pending(UUID.randomUUID(), UUID.randomUUID(), "Spam");
        given(reportRepo.findByStatus(ReviewReportStatus.PENDING)).willReturn(reactor.core.publisher.Flux.just(report));

        StepVerifier.create(new ConsultReviewReportsUseCase(reportRepo)
                        .handle(ReviewReportStatus.PENDING, 10))
                .assertNext(found -> assertThat(found.status()).isEqualTo(ReviewReportStatus.PENDING))
                .verifyComplete();
    }

    @Test
    void acceptingAReportHidesTheReviewAndPublishesTheChange() {
        var review = review(ReviewItemType.ACTIVITY, UUID.randomUUID());
        var report = ReviewReport.pending(review.getId(), UUID.randomUUID(), "Spam");
        given(reportRepo.findById(report.id())).willReturn(Mono.just(report));
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(reviewRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        given(reportRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(new ResolveReviewReportUseCase(reportRepo, reviewRepo, eventRepo, activityRepo, publisher)
                        .handle(report.id(), ReviewReportStatus.RESOLVED, UUID.randomUUID(), "ADMIN"))
                .assertNext(updated -> assertThat(updated.status()).isEqualTo(ReviewReportStatus.RESOLVED))
                .verifyComplete();

        then(reviewRepo).should().save(org.mockito.ArgumentMatchers.argThat(
                saved -> saved.getStatus() == ReviewStatus.HIDDEN));
        then(outbox).should().enqueue(org.mockito.ArgumentMatchers.argThat(
                event -> event.payload().contains("\"status\":\"HIDDEN\"")));
    }

    @Test
    void dismissingAReportKeepsTheReviewPublished() {
        var review = review(ReviewItemType.ACTIVITY, UUID.randomUUID());
        var report = ReviewReport.pending(review.getId(), UUID.randomUUID(), "Spam");
        given(reportRepo.findById(report.id())).willReturn(Mono.just(report));
        given(reportRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(new ResolveReviewReportUseCase(reportRepo, reviewRepo, eventRepo, activityRepo, publisher)
                        .handle(report.id(), ReviewReportStatus.DISMISSED, UUID.randomUUID(), "ADMIN"))
                .assertNext(updated -> assertThat(updated.status()).isEqualTo(ReviewReportStatus.DISMISSED))
                .verifyComplete();

        then(reviewRepo).shouldHaveNoInteractions();
        then(outbox).shouldHaveNoInteractions();
    }

    @Test
    void adminCanModerateAReviewAndPublishesTheChange() {
        var review = review(ReviewItemType.ACTIVITY, UUID.randomUUID());
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(reviewRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(new ModerateReviewUseCase(reviewRepo, publisher)
                        .handle(review.getId(), ReviewStatus.HIDDEN, UUID.randomUUID(), "ADMIN"))
                .assertNext(updated -> assertThat(updated.getStatus()).isEqualTo(ReviewStatus.HIDDEN))
                .verifyComplete();

        then(outbox).should().enqueue(org.mockito.ArgumentMatchers.argThat(
                event -> event.payload().contains("\"status\":\"HIDDEN\"")));
    }

    @Test
    void nonAdminCannotModerateAReview() {
        var review = review(ReviewItemType.ACTIVITY, UUID.randomUUID());

        StepVerifier.create(new ModerateReviewUseCase(reviewRepo, publisher)
                        .handle(review.getId(), ReviewStatus.HIDDEN, UUID.randomUUID(), "GUIDE"))
                .expectError(ReviewModerationNotAllowedException.class)
                .verify();
    }

    @Test
    void listsReviewsInTheModerationQueue() {
        var review = review(ReviewItemType.ACTIVITY, UUID.randomUUID()).withStatus(
                ReviewStatus.PENDING_REVIEW);
        given(reviewRepo.findByStatus(ReviewStatus.PENDING_REVIEW, 10))
                .willReturn(reactor.core.publisher.Flux.just(review));

        StepVerifier.create(new ConsultReviewsForModerationUseCase(reviewRepo)
                        .handle(ReviewStatus.PENDING_REVIEW, 10))
                .assertNext(found -> assertThat(found.getStatus()).isEqualTo(ReviewStatus.PENDING_REVIEW))
                .verifyComplete();
    }

    @Test
    void rejectsResolvingAnAlreadyResolvedReport() {
        var report = ReviewReport.pending(UUID.randomUUID(), UUID.randomUUID(), "Spam")
                .resolve(ReviewReportStatus.RESOLVED, UUID.randomUUID());
        given(reportRepo.findById(report.id())).willReturn(Mono.just(report));

        StepVerifier.create(new ResolveReviewReportUseCase(reportRepo, reviewRepo, eventRepo, activityRepo, publisher)
                        .handle(report.id(), ReviewReportStatus.DISMISSED, UUID.randomUUID(), "ADMIN"))
                .expectError(InvalidReviewException.class)
                .verify();
    }

    @Test
    void onlyTheEntityInChargeCanResolveAReport() {
        var managerId = UUID.randomUUID();
        var review = review(ReviewItemType.EVENT, UUID.randomUUID());
        var report = ReviewReport.pending(review.getId(), UUID.randomUUID(), "Spam");
        given(reportRepo.findById(report.id())).willReturn(Mono.just(report));
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(eventRepo.findById(review.getItemId())).willReturn(Mono.just(event(managerId)));

        var useCase = new ResolveReviewReportUseCase(reportRepo, reviewRepo, eventRepo, activityRepo, publisher);

        StepVerifier.create(useCase.handle(report.id(), ReviewReportStatus.RESOLVED,
                        UUID.randomUUID(), "DESTINATION_MANAGER"))
                .expectError(ReviewReportNotAllowedException.class)
                .verify();
    }

    @Test
    void theManagerOfTheEventCanResolveItsReport() {
        var managerId = UUID.randomUUID();
        var review = review(ReviewItemType.EVENT, UUID.randomUUID());
        var report = ReviewReport.pending(review.getId(), UUID.randomUUID(), "Spam");
        given(reportRepo.findById(report.id())).willReturn(Mono.just(report));
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(eventRepo.findById(review.getItemId())).willReturn(Mono.just(event(managerId)));
        given(reportRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(new ResolveReviewReportUseCase(reportRepo, reviewRepo, eventRepo, activityRepo, publisher)
                        .handle(report.id(), ReviewReportStatus.DISMISSED, managerId, "DESTINATION_MANAGER"))
                .assertNext(updated -> assertThat(updated.status()).isEqualTo(ReviewReportStatus.DISMISSED))
                .verifyComplete();
    }

    @Test
    void theGuideOfTheActivityCanResolveItsReport() {
        var guideId = UUID.randomUUID();
        var review = review(ReviewItemType.ACTIVITY, UUID.randomUUID());
        var report = ReviewReport.pending(review.getId(), UUID.randomUUID(), "Spam");
        given(reportRepo.findById(report.id())).willReturn(Mono.just(report));
        given(reviewRepo.findById(review.getId())).willReturn(Mono.just(review));
        given(reviewRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        given(activityRepo.findById(review.getItemId())).willReturn(Mono.just(activity(guideId)));
        given(reportRepo.save(any())).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(new ResolveReviewReportUseCase(reportRepo, reviewRepo, eventRepo, activityRepo, publisher)
                        .handle(report.id(), ReviewReportStatus.RESOLVED, guideId, "GUIDE"))
                .assertNext(updated -> assertThat(updated.status()).isEqualTo(ReviewReportStatus.RESOLVED))
                .verifyComplete();

        then(reviewRepo).should().save(org.mockito.ArgumentMatchers.argThat(
                saved -> saved.getStatus() == ReviewStatus.HIDDEN));
    }

    @Test
    void computesReviewMetrics() {
        var itemId = UUID.randomUUID();
        given(eventRepo.findById(itemId)).willReturn(Mono.just(event(UUID.randomUUID())));
        given(reviewRepo.metrics(itemId, ReviewItemType.EVENT))
                .willReturn(Mono.just(new ReviewMetrics(itemId, ReviewItemType.EVENT, 2, 4.5)));

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
