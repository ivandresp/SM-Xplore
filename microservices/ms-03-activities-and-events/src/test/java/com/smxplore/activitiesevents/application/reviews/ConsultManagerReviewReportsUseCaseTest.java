package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import com.smxplore.activitiesevents.domain.model.review.ReviewUser;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewReportRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ConsultManagerReviewReportsUseCaseTest {

    @Mock
    private EventRepositoryPort eventRepo;

    @Mock
    private ReviewRepositoryPort reviewRepo;

    @Mock
    private ReviewReportRepositoryPort reportRepo;

    @Test
    void listsOnlyTheReportsOfTheManagerSites() {
        var managerId = UUID.randomUUID();
        var siteId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var review = Review.builder()
                .itemId(eventId)
                .itemType(ReviewItemType.EVENT)
                .user(new ReviewUser(UUID.randomUUID(), "Ana"))
                .rating(5)
                .build();
        var report = ReviewReport.pending(review.getId(), UUID.randomUUID(), "Spam");

        given(eventRepo.findIdsBySiteIds(List.of(siteId))).willReturn(Flux.just(eventId));
        given(reviewRepo.findByItemIds(List.of(eventId), ReviewItemType.EVENT)).willReturn(Flux.just(review));
        given(reportRepo.findByStatusAndReviewIds(ReviewReportStatus.PENDING, List.of(review.getId()), 10))
                .willReturn(Flux.just(report));

        var useCase = new ConsultManagerReviewReportsUseCase(
                catalog(siteId), eventRepo, reviewRepo, reportRepo);

        StepVerifier.create(useCase.handle(managerId, "token", ReviewReportStatus.PENDING, 10))
                .assertNext(found -> assertThat(found.id()).isEqualTo(report.id()))
                .verifyComplete();
    }

    @Test
    void returnsNothingWhenTheManagerHasNoSites() {
        var useCase = new ConsultManagerReviewReportsUseCase(
                catalog(), eventRepo, reviewRepo, reportRepo);

        StepVerifier.create(useCase.handle(UUID.randomUUID(), "token", ReviewReportStatus.PENDING, 10))
                .verifyComplete();

        verifyNoInteractions(eventRepo, reviewRepo, reportRepo);
    }

    private static SiteCatalogPort catalog(UUID... siteIds) {
        return new SiteCatalogPort() {
            @Override
            public reactor.core.publisher.Mono<SiteSnapshot> findById(UUID siteId, String bearerToken) {
                return reactor.core.publisher.Mono.empty();
            }

            @Override
            public Flux<SiteSnapshot> findByManagerId(UUID managerId, String bearerToken) {
                return Flux.fromArray(siteIds)
                        .map(id -> new SiteSnapshot(id, managerId, "Sitio"));
            }

            @Override
            public Flux<SiteSnapshot> findByZoneId(UUID zoneId, String bearerToken) {
                return Flux.empty();
            }
        };
    }
}
