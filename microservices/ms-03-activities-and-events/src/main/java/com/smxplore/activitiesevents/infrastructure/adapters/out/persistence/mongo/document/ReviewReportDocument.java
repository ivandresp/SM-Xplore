package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document;

import com.smxplore.activitiesevents.domain.model.review.ReviewReportStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "review_reports")
@CompoundIndex(name = "review_report_review_reporter", def = "{'reviewId': 1, 'reporterId': 1}", unique = true)
@CompoundIndex(name = "review_report_review", def = "{'reviewId': 1}")
@CompoundIndex(name = "review_report_status_created", def = "{'status': 1, 'createdAt': 1}")
public record ReviewReportDocument(
        @Id UUID id,
        UUID reviewId,
        UUID reporterId,
        String reason,
        ReviewReportStatus status,
        UUID resolvedBy,
        Instant resolvedAt,
        Instant createdAt
) {
}
