package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document;

import com.smxplore.activitiesevents.domain.model.review.ReviewAiAssessment;

import java.time.Instant;
import java.util.List;

public record ReviewAiAssessmentDocument(
        ReviewAiAssessment.Status status,
        boolean flagged,
        double score,
        List<String> categories,
        String reason,
        String model,
        Instant checkedAt
) {
}
