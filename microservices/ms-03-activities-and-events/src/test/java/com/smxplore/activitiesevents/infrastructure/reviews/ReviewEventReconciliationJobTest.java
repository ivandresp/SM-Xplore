package com.smxplore.activitiesevents.infrastructure.reviews;

import com.smxplore.activitiesevents.application.reviews.ReviewEventMetrics;
import com.smxplore.activitiesevents.application.reviews.ReviewEventPublisher;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewUser;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ReviewEventReconciliationJobTest {

    @Mock
    private ReviewRepositoryPort reviews;
    @Mock
    private ReviewEventPublisher publisher;
    @Mock
    private ReviewEventMetrics metrics;

    private final ReviewEventReconciliationProperties properties = new ReviewEventReconciliationProperties();

    @Test
    void doesNothingWhenDisabled() {
        properties.setEnabled(false);

        StepVerifier.create(job().reconcile()).verifyComplete();

        then(reviews).shouldHaveNoInteractions();
        then(publisher).shouldHaveNoInteractions();
    }

    @Test
    void redispatchesEveryPendingReview() {
        var first = review(Instant.parse("2026-11-04T20:00:00Z"));
        var second = review(Instant.parse("2026-11-04T21:00:00Z"));
        given(reviews.findPendingEventPublication(any(Instant.class), eq(properties.getBatchSize())))
                .willReturn(Flux.just(first, second));
        given(publisher.redispatch(any(Review.class))).willAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var startedAt = Instant.now();
        StepVerifier.create(job().reconcile()).verifyComplete();

        then(publisher).should().redispatch(first);
        then(publisher).should().redispatch(second);
        then(metrics).should(times(2)).reconciled();
        // La ventana de gracia se resta al reloj: ninguna reseña recién escrita entra en el barrido.
        then(reviews).should().findPendingEventPublication(
                argThat(cutoff -> cutoff.isBefore(startedAt)), anyInt());
    }

    @Test
    void swallowsRepositoryFailures() {
        given(reviews.findPendingEventPublication(any(Instant.class), anyInt()))
                .willReturn(Flux.error(new IllegalStateException("mongo down")));

        StepVerifier.create(job().reconcile()).verifyComplete();

        then(publisher).should(never()).redispatch(any());
        then(metrics).should(never()).reconciled();
    }

    @Test
    void respectsTheConfiguredBatchSize() {
        properties.setBatchSize(7);
        given(reviews.findPendingEventPublication(any(Instant.class), eq(7))).willReturn(Flux.empty());

        StepVerifier.create(job().reconcile()).verifyComplete();

        then(reviews).should().findPendingEventPublication(any(Instant.class), eq(7));
    }

    private ReviewEventReconciliationJob job() {
        return new ReviewEventReconciliationJob(reviews, publisher, metrics, properties);
    }

    private static Review review(Instant updatedAt) {
        return Review.builder()
                .id(UUID.randomUUID())
                .itemId(UUID.randomUUID())
                .itemType(ReviewItemType.ACTIVITY)
                .user(new ReviewUser(UUID.randomUUID(), "Ana"))
                .rating(4)
                .updatedAt(updatedAt)
                .build();
    }
}
