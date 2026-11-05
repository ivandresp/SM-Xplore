package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review;

import com.smxplore.sitesandservices.domain.model.review.ReviewAiAssessment;

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
