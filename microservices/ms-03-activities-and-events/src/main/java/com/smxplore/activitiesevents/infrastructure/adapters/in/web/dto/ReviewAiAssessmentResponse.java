package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.review.ReviewAiAssessment;

import java.time.Instant;
import java.util.List;

public record ReviewAiAssessmentResponse(
        String status,
        boolean flagged,
        double score,
        List<String> categories,
        String reason,
        String model,
        Instant checkedAt
) {

    public static ReviewAiAssessmentResponse from(ReviewAiAssessment assessment) {
        if (assessment == null) {
            return null;
        }
        return new ReviewAiAssessmentResponse(
                assessment.status().name(),
                assessment.flagged(),
                assessment.score(),
                assessment.categories(),
                assessment.reason(),
                assessment.model(),
                assessment.checkedAt()
        );
    }
}
