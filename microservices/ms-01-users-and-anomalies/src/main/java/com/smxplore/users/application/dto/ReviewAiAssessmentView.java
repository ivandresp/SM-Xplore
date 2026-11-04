package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.ReviewAiAssessment;
import java.time.Instant;
import java.util.List;

public record ReviewAiAssessmentView(
        String status,
        boolean flagged,
        double score,
        List<String> categories,
        String reason,
        String model,
        Instant checkedAt) {

    public static ReviewAiAssessmentView from(ReviewAiAssessment assessment) {
        if (assessment == null) {
            return null;
        }
        return new ReviewAiAssessmentView(
                assessment.status().name(),
                assessment.flagged(),
                assessment.score(),
                assessment.categories(),
                assessment.reason(),
                assessment.model(),
                assessment.checkedAt());
    }
}
