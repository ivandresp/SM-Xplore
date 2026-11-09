package com.smxplore.sitesandservices.application.security;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.review.ReviewCreateService;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.review.CatalogTarget;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewAiAction;
import com.smxplore.sitesandservices.domain.model.review.ReviewAiPolicy;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.domain.ports.out.ReservationRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ReviewAiFilter;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MS04-12: crear una reseña exige una reserva COMPLETED previa del servicio (o de algun servicio
 * del sitio) y la politica de IA es fail-closed.
 */
class ReviewConsumptionProofServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final UUID TOURIST_ID = UUID.randomUUID();

    private final ReviewRepositoryPort reviewRepository = mock(ReviewRepositoryPort.class);
    private final CatalogLookup lookup = mock(CatalogLookup.class);
    private final ReviewAiFilter aiFilter = mock(ReviewAiFilter.class);
    private final TransactionalOutboxWriter outboxWriter = mock(TransactionalOutboxWriter.class);
    private final ReservationRepositoryPort reservationRepository =
            mock(ReservationRepositoryPort.class);
    private final ServiceRepositoryPort serviceRepository = mock(ServiceRepositoryPort.class);

    private final Actor tourist = new Actor(TOURIST_ID, UserRole.TOURIST, "Turista");

    @BeforeEach
    void defaultLookups() {
        // Los argumentos de Mono.then se evaluan de inmediato; sin este stub el mock devolveria
        // null incluso en las rutas que fallan antes de consultar duplicados.
        when(reviewRepository.findByTouristAndItem(any(), any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void rejectsReviewsWithoutACompletedBooking() {
        ReviewCreateService service = service(ItemType.SERVICE, SERVICE_ID, true);
        when(lookup.publishedReviewTarget(ItemType.SERVICE, SERVICE_ID))
                .thenReturn(Mono.just(target(ItemType.SERVICE, SERVICE_ID)));
        when(reservationRepository.existsCompletedBooking(any(), any()))
                .thenReturn(Mono.just(false));

        StepVerifier.create(service.handle(request(ItemType.SERVICE, SERVICE_ID), tourist, "corr"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ForbiddenOperationException.class)
                        .hasMessageContaining("completed reservation"))
                .verify();

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void acceptsReviewsWithACompletedBooking() {
        ReviewCreateService service = service(ItemType.SERVICE, SERVICE_ID, true);
        when(lookup.publishedReviewTarget(ItemType.SERVICE, SERVICE_ID))
                .thenReturn(Mono.just(target(ItemType.SERVICE, SERVICE_ID)));
        when(reservationRepository.existsCompletedBooking(any(), any()))
                .thenReturn(Mono.just(true));
        when(reviewRepository.findByTouristAndItem(any(), any(), any())).thenReturn(Mono.empty());
        when(reviewRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(outboxWriter.save(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Mono.class));

        Review created = service.handle(request(ItemType.SERVICE, SERVICE_ID), tourist, "corr").block();

        assertThat(created).isNotNull();
        assertThat(created.status()).isEqualTo(ReviewStatus.PUBLISHED);
        verify(reviewRepository).save(any());
    }

    @Test
    void siteReviewsRequireACompletedBookingOnSomeServiceOfTheSite() {
        ReviewCreateService service = service(ItemType.SITE, SITE_ID, true);
        when(lookup.publishedReviewTarget(ItemType.SITE, SITE_ID))
                .thenReturn(Mono.just(target(ItemType.SITE, SITE_ID)));
        when(serviceRepository.findIdsBySiteId(SITE_ID)).thenReturn(Flux.empty());

        StepVerifier.create(service.handle(request(ItemType.SITE, SITE_ID), tourist, "corr"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ForbiddenOperationException.class)
                        .hasMessageContaining("completed reservation"))
                .verify();

        verifyNoInteractions(reservationRepository);
    }

    @Test
    void theControlCanBeDisabledForTests() {
        ReviewCreateService service = service(ItemType.SERVICE, SERVICE_ID, false);
        when(lookup.publishedReviewTarget(ItemType.SERVICE, SERVICE_ID))
                .thenReturn(Mono.just(target(ItemType.SERVICE, SERVICE_ID)));
        when(reviewRepository.findByTouristAndItem(any(), any(), any())).thenReturn(Mono.empty());
        when(reviewRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(outboxWriter.save(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Mono.class));

        assertThat(service.handle(request(ItemType.SERVICE, SERVICE_ID), tourist, "corr").block())
                .isNotNull();
        verifyNoInteractions(reservationRepository);
    }

    private ReviewCreateService service(ItemType itemType, UUID itemId, boolean requireBooking) {
        return new ReviewCreateService(
                reviewRepository,
                lookup,
                new CatalogAccess(),
                aiFilter,
                new ReviewAiPolicy(0.7, ReviewAiAction.PENDING_REVIEW, false),
                outboxWriter,
                mock(DomainEventFactory.class),
                CLOCK,
                reservationRepository,
                serviceRepository,
                requireBooking);
    }

    private static CatalogTarget target(ItemType itemType, UUID itemId) {
        return new CatalogTarget(
                itemType, itemId, "Objeto", UUID.randomUUID(), ContentStatus.PUBLISHED);
    }

    private static Review request(ItemType itemType, UUID itemId) {
        return Review.builder()
                .itemType(itemType)
                .itemId(itemId)
                .rating(5)
                .build();
    }
}
