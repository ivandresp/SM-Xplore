package com.smxplore.activitiesevents.application.reviews;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.review.InvalidReviewException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.review.ReviewReportNotAllowedException;
import com.smxplore.activitiesevents.domain.model.review.ReviewReport;
import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewReportRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.review.ResolveReviewReportUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Resolves or dismisses a report. Only {@code ADMIN} or the entity in charge of the reviewed item
 * can act on it: the guide of the activity or the manager of the event, the same rule that governs
 * the review response.
 */
@RequiredArgsConstructor
public class ResolveReviewReportUseCase implements ResolveReviewReportUseCasePort {

    private static final String ADMIN = "ADMIN";
    private static final String DESTINATION_MANAGER = "DESTINATION_MANAGER";
    private static final String GUIDE = "GUIDE";

    private final ReviewReportRepositoryPort reportRepo;
    private final ReviewRepositoryPort reviewRepo;
    private final EventRepositoryPort eventRepo;
    private final ActivityRepositoryPort activityRepo;

    @Override
    public Mono<ReviewReport> handle(UUID reportId, ReviewReportStatus status, UUID resolverId, String role) {
        if (status != ReviewReportStatus.RESOLVED && status != ReviewReportStatus.DISMISSED) {
            return Mono.error(new InvalidReviewException("Report can only be resolved or dismissed."));
        }
        return reportRepo.findById(reportId)
                .switchIfEmpty(Mono.error(new ReviewNotFoundException(reportId)))
                .flatMap(report -> authorize(report, resolverId, role).thenReturn(report))
                .flatMap(report -> {
                    if (report.status() != ReviewReportStatus.PENDING) {
                        return Mono.error(new InvalidReviewException("The report was already resolved."));
                    }
                    return reportRepo.save(report.resolve(status, resolverId));
                });
    }

    private Mono<Void> authorize(ReviewReport report, UUID resolverId, String role) {
        if (ADMIN.equals(role)) {
            return Mono.empty();
        }
        return reviewRepo.findById(report.reviewId())
                .switchIfEmpty(Mono.error(new ReviewNotFoundException(report.reviewId())))
                .flatMap(review -> switch (review.getItemType()) {
                    case EVENT -> eventRepo.findById(review.getItemId())
                            .switchIfEmpty(Mono.error(new EventNotFoundException(review.getItemId())))
                            .flatMap(event -> requireOwner(event.getManagerId(), resolverId, role,
                                    DESTINATION_MANAGER));
                    case ACTIVITY -> activityRepo.findById(review.getItemId())
                            .switchIfEmpty(Mono.error(new ActivityNotFoundException(review.getItemId())))
                            .flatMap(activity -> requireOwner(activity.getGuideId(), resolverId, role,
                                    GUIDE));
                });
    }

    private static Mono<Void> requireOwner(UUID ownerId, UUID resolverId, String role, String requiredRole) {
        return requiredRole.equals(role) && resolverId.equals(ownerId)
                ? Mono.empty()
                : Mono.error(new ReviewReportNotAllowedException(
                        "Only the entity in charge of the reviewed item can resolve this report."));
    }
}
