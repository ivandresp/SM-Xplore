package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewReportRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Inbox of a guide: the pending reports about the reviews of the activities it owns.
 *
 * <p>{@link ResolveReviewReportUseCase} already lets a guide resolve a report of its own activity,
 * but without this list the guide had to guess the report id: the administrative list is
 * admin/manager only. The scope is the guide's activities, so a report about an event never shows
 * up here.
 */
@RequiredArgsConstructor
public class ConsultGuideReviewReportsUseCase {

    private final ActivityRepositoryPort activityRepository;
    private final ReviewRepositoryPort reviewRepository;
    private final ReviewReportRepositoryPort reportRepository;

    public Flux<ReviewReport> handle(UUID guideId, ReviewReportStatus status, int limit) {
        if (guideId == null) {
            return Flux.empty();
        }
        return activityRepository.findAllByGuideId(guideId)
                .map(activity -> activity.getId())
                .collectList()
                .flatMapMany(activityIds -> {
                    if (activityIds.isEmpty()) {
                        return Flux.empty();
                    }
                    return reviewRepository.findByItemIds(activityIds, ReviewItemType.ACTIVITY)
                            .map(review -> review.getId())
                            .collectList()
                            .flatMapMany(reviewIds -> reviewIds.isEmpty()
                                    ? Flux.empty()
                                    : reportRepository.findByStatusAndReviewIds(status, reviewIds, limit));
                });
    }
}
