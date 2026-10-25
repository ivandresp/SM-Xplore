package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "review_reports")
@CompoundIndex(name = "review_report_review_reporter", def = "{'reviewId': 1, 'reporterId': 1}", unique = true)
@CompoundIndex(name = "review_report_review", def = "{'reviewId': 1}")
public record ReviewReportDocument(
        @Id UUID id,
        UUID reviewId,
        UUID reporterId,
        String reason,
        Instant createdAt
) {
}
