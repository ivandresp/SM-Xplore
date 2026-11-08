package com.smxplore.sitesandservices.application.security;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.review.ReviewRespondService;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.exception.ReviewResponseConflictException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.review.CatalogTarget;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MS04-07: solo se responde una resena PUBLICADA y sin respuesta previa; el resto es 409.
 */
class ReviewResponseConflictServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();

    private final ReviewRepositoryPort reviewRepository = mock(ReviewRepositoryPort.class);
    private final CatalogLookup lookup = mock(CatalogLookup.class);
    private final TransactionalOutboxWriter outboxWriter = mock(TransactionalOutboxWriter.class);
    private final DomainEventFactory eventFactory = mock(DomainEventFactory.class);

    private final ReviewRespondService service = new ReviewRespondService(
            reviewRepository, lookup, new CatalogAccess(), outboxWriter, eventFactory, CLOCK);

    private final Actor admin = new Actor(UUID.randomUUID(), UserRole.ADMIN, "Admin");

    @Test
    void cannotAnswerAHiddenReview() {
        stubLookups(review(ReviewStatus.HIDDEN, null));

        StepVerifier.create(service.handle(response("Gracias"), admin, "corr"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ReviewResponseConflictException.class)
                        .hasMessageContaining("Only published reviews"))
                .verify();

        verifyNoInteractions(reviewRepository);
    }

    @Test
    void cannotOverwriteAnExistingResponse() {
        stubLookups(review(ReviewStatus.PUBLISHED, "Ya respondida"));

        StepVerifier.create(service.handle(response("Otra"), admin, "corr"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ReviewResponseConflictException.class)
                        .hasMessageContaining("already has a response"))
                .verify();

        verifyNoInteractions(reviewRepository);
    }

    @Test
    void answersAPublishedReviewWithoutResponse() {
        Review existing = review(ReviewStatus.PUBLISHED, null);
        stubLookups(existing);
        when(reviewRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(outboxWriter.save(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Mono.class));

        Review updated = service.handle(response("Gracias"), admin, "corr").block();

        assertThat(updated).isNotNull();
        assertThat(updated.response()).isEqualTo("Gracias");
        verify(reviewRepository).save(any());
    }

    private void stubLookups(Review existing) {
        when(lookup.review(REVIEW_ID)).thenReturn(Mono.just(existing));
        when(lookup.reviewTarget(existing.itemType(), existing.itemId()))
                .thenReturn(Mono.just(new CatalogTarget(
                        existing.itemType(),
                        existing.itemId(),
                        existing.itemName(),
                        OWNER_ID,
                        ContentStatus.PUBLISHED)));
    }

    private static Review response(String response) {
        return Review.builder().reviewId(REVIEW_ID).response(response).build();
    }

    private static Review review(ReviewStatus status, String response) {
        return Review.builder()
                .reviewId(REVIEW_ID)
                .touristId(UUID.randomUUID())
                .touristName("Turista")
                .itemType(ItemType.SERVICE)
                .itemId(UUID.randomUUID())
                .itemName("Servicio")
                .rating(5)
                .status(status)
                .response(response)
                .respondedAt(response == null ? null : CLOCK.instant())
                .createdAt(CLOCK.instant())
                .updatedAt(CLOCK.instant())
                .build();
    }
}
