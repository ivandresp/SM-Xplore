package com.smxplore.activitiesevents.application.reviews;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smxplore.activitiesevents.application.outbox.ReviewEventFactory;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewUser;
import com.smxplore.activitiesevents.domain.model.outbox.OutboxEvent;
import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * La reseña ya está guardada cuando se publica el evento: ningún fallo del outbox puede tumbar la
 * respuesta, y solo se marca {@code eventPublishedAt} cuando el enqueue se confirmó.
 */
@ExtendWith(MockitoExtension.class)
class ReviewEventPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ReviewEventFactory eventFactory = new ReviewEventFactory(objectMapper);

    @Mock
    private OutboxPort outbox;
    @Mock
    private ReviewRepositoryPort reviews;
    @Mock
    private ReviewEventMetrics metrics;

    @Test
    void marksTheReviewWhenTheOutboxAcceptsTheEvent() {
        var review = review();
        given(outbox.enqueue(any(OutboxEvent.class))).willReturn(Mono.empty());
        given(reviews.markEventPublished(eq(review.getId()), any(Instant.class))).willReturn(Mono.just(true));

        StepVerifier.create(publisher().publish(review))
                .expectNext(review)
                .verifyComplete();

        then(outbox).should().enqueue(any(OutboxEvent.class));
        then(reviews).should().markEventPublished(eq(review.getId()), any(Instant.class));
        then(metrics).should().published();
        then(metrics).should(never()).failed();
    }

    @Test
    void keepsTheRequestAliveAndLeavesTheReviewPendingWhenTheOutboxFails() {
        var review = review();
        given(outbox.enqueue(any(OutboxEvent.class)))
                .willReturn(Mono.error(new IllegalStateException("outbox down")));

        StepVerifier.create(publisher().publish(review))
                .expectNext(review)
                .verifyComplete();

        then(reviews).should(never()).markEventPublished(any(), any());
        then(metrics).should().failed();
        then(metrics).should(never()).published();
    }

    @Test
    void doesNotFailWhenTheReviewCouldNotBeMarked() {
        var review = review();
        given(outbox.enqueue(any(OutboxEvent.class))).willReturn(Mono.empty());
        given(reviews.markEventPublished(eq(review.getId()), any(Instant.class))).willReturn(Mono.just(false));

        StepVerifier.create(publisher().publish(review))
                .expectNext(review)
                .verifyComplete();

        then(metrics).should().published();
    }

    @Test
    void redispatchFollowsTheSamePathAsAPublish() {
        var review = review();
        given(outbox.enqueue(any(OutboxEvent.class))).willReturn(Mono.empty());
        given(reviews.markEventPublished(eq(review.getId()), any(Instant.class))).willReturn(Mono.just(true));

        StepVerifier.create(publisher().redispatch(review))
                .expectNext(review)
                .verifyComplete();

        then(reviews).should().markEventPublished(eq(review.getId()), any(Instant.class));
        then(metrics).should().published();
    }

    private ReviewEventPublisher publisher() {
        return new ReviewEventPublisher(outbox, eventFactory, reviews, metrics);
    }

    private static Review review() {
        return Review.builder()
                .id(UUID.randomUUID())
                .itemId(UUID.randomUUID())
                .itemType(ReviewItemType.EVENT)
                .user(new ReviewUser(UUID.randomUUID(), "Ana"))
                .rating(5)
                .updatedAt(Instant.parse("2026-11-04T22:00:00Z"))
                .build();
    }
}
