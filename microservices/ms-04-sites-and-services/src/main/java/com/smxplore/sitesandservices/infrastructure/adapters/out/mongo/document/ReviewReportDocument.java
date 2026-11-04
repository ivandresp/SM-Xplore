package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "review_reports")
public class ReviewReportDocument {

    @Id
    private String id;
    private String reviewId;
    private String itemType;
    private String itemId;
    private String reportedBy;
    private String reportedByName;
    private String reason;
    private String details;
    private String status;
    private Instant createdAt;
    private Instant resolvedAt;
    private String resolvedBy;
    private String resolution;
}
