package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewReportRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Scopes the review report list to the catalog of a destination manager: its sites through ms-04,
 * the events of those sites, the reviews of those events and finally their reports.
 *
 * <p>A report about an activity is not visible here: activities belong to a guide, not to a site
 * manager, and only {@code ADMIN} sees the global list.
 */
@RequiredArgsConstructor
public class ConsultManagerReviewReportsUseCase {

    private final SiteCatalogPort siteCatalog;
    private final EventRepositoryPort eventRepository;
    private final ReviewRepositoryPort reviewRepository;
    private final ReviewReportRepositoryPort reportRepository;

    public Flux<ReviewReport> handle(UUID managerId, String bearerToken, ReviewReportStatus status, int limit) {
        return siteCatalog.findByManagerId(managerId, bearerToken)
                .map(SiteCatalogPort.SiteSnapshot::siteId)
                .collectList()
                .flatMapMany(siteIds -> {
                    if (siteIds.isEmpty()) {
                        return Flux.empty();
                    }
                    return eventRepository.findIdsBySiteIds(siteIds)
                            .collectList()
                            .flatMapMany(eventIds -> {
                                if (eventIds.isEmpty()) {
                                    return Flux.empty();
                                }
                                return reviewRepository.findByItemIds(eventIds, ReviewItemType.EVENT)
                                        .map(review -> review.getId())
                                        .collectList()
                                        .flatMapMany(reviewIds -> reviewIds.isEmpty()
                                                ? Flux.empty()
                                                : reportRepository.findByStatusAndReviewIds(
                                                        status, reviewIds, limit));
                            });
                });
    }
}
