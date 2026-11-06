package com.smxplore.activitiesevents.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewUser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewEventFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ReviewEventFactory factory = new ReviewEventFactory(objectMapper);

    @Test
    void buildsTheV1EnvelopeWithoutTheFreeTextComment() throws Exception {
        var review = review(ReviewItemType.ACTIVITY, 4);

        var event = factory.reviewChanged(review).block();

        assertThat(event).isNotNull();
        assertThat(event.eventType()).isEqualTo("reviews.review.changed");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.aggregateId()).isEqualTo(review.getId());

        var envelope = objectMapper.readTree(event.payload());
        assertThat(envelope.get("eventType").asText()).isEqualTo("reviews.review.changed");
        assertThat(envelope.get("producer").asText()).isEqualTo("ms-03-activities-and-events");

        var payload = envelope.get("payload");
        assertThat(payload.get("reviewId").asText()).isEqualTo(review.getId().toString());
        assertThat(payload.get("touristId").asText()).isEqualTo(review.getUser().id().toString());
        assertThat(payload.get("itemType").asText()).isEqualTo("ACTIVITY");
        assertThat(payload.get("itemId").asText()).isEqualTo(review.getItemId().toString());
        assertThat(payload.get("rating").asInt()).isEqualTo(4);
        assertThat(payload.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(payload.has("comment")).isFalse();
    }

    @Test
    void usesTheUpdateTimestampOfTheReview() throws Exception {
        var updatedAt = Instant.parse("2026-11-04T22:00:00Z");
        var review = Review.builder()
                .itemId(UUID.randomUUID())
                .itemType(ReviewItemType.EVENT)
                .user(new ReviewUser(UUID.randomUUID(), "Ana"))
                .rating(5)
                .updatedAt(updatedAt)
                .build();

        var event = factory.reviewChanged(review).block();

        assertThat(event).isNotNull();
        var payload = objectMapper.readTree(event.payload()).get("payload");
        assertThat(payload.get("updatedAt").asText()).isEqualTo(updatedAt.toString());
    }

    @Test
    void derivesADeterministicEventIdFromTheReviewAndItsUpdate() {
        var review = review(ReviewItemType.EVENT, 5);

        var first = factory.reviewChanged(review).block();
        var second = factory.reviewChanged(review).block();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.id()).isEqualTo(second.id());
        assertThat(first.id()).isEqualTo(UUID.nameUUIDFromBytes(
                ("reviews.review.changed:" + review.getId() + ":" + review.updatedAt())
                        .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void changesTheEventIdWhenTheReviewChangesAgain() {
        var updatedAt = Instant.parse("2026-11-04T22:00:00Z");
        var review = Review.builder()
                .itemId(UUID.randomUUID())
                .itemType(ReviewItemType.EVENT)
                .user(new ReviewUser(UUID.randomUUID(), "Ana"))
                .rating(5)
                .updatedAt(updatedAt)
                .build();
        var laterChange = review.toBuilder().updatedAt(updatedAt.plusSeconds(1)).build();

        var first = factory.reviewChanged(review).block();
        var second = factory.reviewChanged(laterChange).block();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        // Un cambio posterior es un evento distinto: el outbox no debe descartarlo como duplicado.
        assertThat(first.id()).isNotEqualTo(second.id());
    }

    private static Review review(ReviewItemType itemType, int rating) {
        return Review.builder()
                .itemId(UUID.randomUUID())
                .itemType(itemType)
                .user(new ReviewUser(UUID.randomUUID(), "Ana"))
                .rating(rating)
                .comment("Texto libre que no debe viajar en el evento")
                .build();
    }
}
