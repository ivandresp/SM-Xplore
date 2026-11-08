package com.smxplore.sitesandservices.application.security;

import com.smxplore.sitesandservices.application.review.ReviewGetService;
import com.smxplore.sitesandservices.application.review.ReviewListService;
import com.smxplore.sitesandservices.application.review.ReviewReportListService;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.exception.ResourceNotFoundException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewFilter;
import com.smxplore.sitesandservices.domain.model.review.ReviewReportFilter;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ReviewReportRepositoryPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MS04-05: las lecturas publicas de resenas no deben lanzar NPE con un actor anonimo y solo
 * deben exponer contenido publicado.
 */
class ReviewVisibilityServiceTest {

    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID TOURIST_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final CatalogLookup lookup = mock(CatalogLookup.class);
    private final ReviewRepositoryPort reviewRepository = mock(ReviewRepositoryPort.class);
    private final ReviewReportRepositoryPort reportRepository = mock(ReviewReportRepositoryPort.class);
    private final CatalogAccess access = new CatalogAccess();

    private final ReviewGetService getService = new ReviewGetService(lookup);
    private final ReviewListService listService = new ReviewListService(reviewRepository);
    private final ReviewReportListService reportListService =
            new ReviewReportListService(reportRepository, access);

    @Test
    void anonymousGetOfHiddenReviewReturns404() {
        when(lookup.review(REVIEW_ID)).thenReturn(Mono.just(review(ReviewStatus.HIDDEN)));

        StepVerifier.create(getService.handle(REVIEW_ID, null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ResourceNotFoundException.class))
                .verify();
    }

    @Test
    void hiddenReviewIsVisibleToItsAuthorAndToTheAdmin() {
        when(lookup.review(REVIEW_ID)).thenReturn(Mono.just(review(ReviewStatus.HIDDEN)));

        assertThat(getService.handle(REVIEW_ID, tourist()).block()).isNotNull();
        assertThat(getService.handle(REVIEW_ID, admin()).block()).isNotNull();
    }

    @Test
    void anonymousListingForcesPublishedStatusAndRejectsStatusOrTouristFilters() {
        when(reviewRepository.findAll(any()))
                .thenReturn(Mono.just(new PageResult<>(List.of(), 0, 20, 0)));

        StepVerifier.create(listService.handle(filter(null, null), null))
                .assertNext(page -> assertThat(page.content()).isEmpty())
                .verifyComplete();

        StepVerifier.create(listService.handle(filter(ReviewStatus.HIDDEN, null), null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ForbiddenOperationException.class))
                .verify();

        StepVerifier.create(listService.handle(filter(null, TOURIST_ID), null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ForbiddenOperationException.class))
                .verify();
    }

    @Test
    void anonymousCannotListReviewReports() {
        StepVerifier.create(reportListService.handle(
                        new ReviewReportFilter(null, null, 0, 20), null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ForbiddenOperationException.class))
                .verify();
    }

    private static ReviewFilter filter(ReviewStatus status, UUID touristId) {
        return new ReviewFilter(ItemType.SERVICE, UUID.randomUUID(), touristId, status, 0, 20);
    }

    private static Review review(ReviewStatus status) {
        return Review.builder()
                .reviewId(REVIEW_ID)
                .touristId(TOURIST_ID)
                .touristName("Turista")
                .itemType(ItemType.SERVICE)
                .itemId(UUID.randomUUID())
                .itemName("Servicio")
                .rating(5)
                .status(status)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private static Actor tourist() {
        return new Actor(TOURIST_ID, UserRole.TOURIST, "Turista");
    }

    private static Actor admin() {
        return new Actor(UUID.randomUUID(), UserRole.ADMIN, "Admin");
    }
}
