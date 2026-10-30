package com.smxplore.users.infrastructure.adapter.out.persistence.mongo;

import com.smxplore.users.domain.model.Review;
import com.smxplore.users.domain.model.ReviewAiAssessment;
import com.smxplore.users.domain.model.ReviewModerationStatus;
import com.smxplore.users.domain.model.ReviewReport;
import com.smxplore.users.domain.model.ReviewResponse;
import com.smxplore.users.domain.model.ReviewTargetType;
import java.util.List;

final class ReviewMongoMapper {

    ReviewDocument toDocument(Review review) {
        ReviewDocument document = new ReviewDocument();
        document.setId(review.id());
        document.setTouristId(review.touristId());
        document.setTargetType(review.targetType().code());
        document.setTargetId(review.targetId());
        document.setRating(review.rating());
        document.setComment(review.comment());
        document.setCreatedAt(review.createdAt());
        document.setUpdatedAt(review.updatedAt());
        document.setModerationStatus(review.moderationStatus().name());
        document.setReported(review.isReported());
        document.setReports(review.reports().stream().map(this::toReport).toList());
        document.setModeratedBy(review.moderatedBy());
        document.setModeratedAt(review.moderatedAt());
        document.setModerationReason(review.moderationReason());
        ReviewAiAssessment ai = review.aiAssessment();
        document.setAiStatus(ai == null ? null : ai.status().name());
        document.setAiFlagged(review.isAiRetained());
        document.setAiScore(ai == null ? 0 : ai.score());
        document.setAiCategories(ai == null ? List.of() : ai.categories());
        document.setAiReason(ai == null ? null : ai.reason());
        document.setAiModel(ai == null ? null : ai.model());
        document.setAiCheckedAt(ai == null ? null : ai.checkedAt());
        document.setResponse(toResponse(review.response()));
        return document;
    }

    Review toDomain(ReviewDocument document) {
        return Review.rehydrate(
                document.getId(),
                document.getTouristId(),
                ReviewTargetType.fromCode(document.getTargetType()),
                document.getTargetId(),
                document.getRating(),
                document.getComment(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                toDomainResponse(document.getResponse()),
                ReviewModerationStatus.valueOf(document.getModerationStatus()),
                document.getReports() == null
                        ? List.of()
                        : document.getReports().stream()
                                .map(this::toDomainReport)
                                .toList(),
                document.getModeratedBy(),
                document.getModeratedAt(),
                document.getModerationReason(),
                toAiAssessment(document),
                document.isAiFlagged());
    }

    private ReviewAiAssessment toAiAssessment(ReviewDocument document) {
        if (document.getAiStatus() == null) {
            return null;
        }
        return new ReviewAiAssessment(
                ReviewAiAssessment.Status.valueOf(document.getAiStatus()),
                document.isAiFlagged(),
                document.getAiScore(),
                document.getAiCategories(),
                document.getAiReason(),
                document.getAiModel(),
                document.getAiCheckedAt());
    }

    private ReviewDocument.Response toResponse(ReviewResponse response) {
        if (response == null) {
            return null;
        }
        ReviewDocument.Response document = new ReviewDocument.Response();
        document.setResponderId(response.responderId());
        document.setText(response.text());
        document.setCreatedAt(response.createdAt());
        document.setUpdatedAt(response.updatedAt());
        return document;
    }

    private ReviewResponse toDomainResponse(ReviewDocument.Response response) {
        if (response == null) {
            return null;
        }
        return new ReviewResponse(
                response.getResponderId(), response.getText(), response.getCreatedAt(), response.getUpdatedAt());
    }

    private ReviewDocument.Report toReport(ReviewReport report) {
        ReviewDocument.Report document = new ReviewDocument.Report();
        document.setReporterId(report.reporterId());
        document.setReason(report.reason());
        document.setReportedAt(report.reportedAt());
        return document;
    }

    private ReviewReport toDomainReport(ReviewDocument.Report report) {
        return new ReviewReport(report.getReporterId(), report.getReason(), report.getReportedAt());
    }
}
