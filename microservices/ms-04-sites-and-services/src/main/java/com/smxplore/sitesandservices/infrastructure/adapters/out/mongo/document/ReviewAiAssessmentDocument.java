package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewAiAssessmentDocument {

    private String status;
    private boolean flagged;
    private double score;
    private List<String> categories;
    private String reason;
    private String model;
    private Instant checkedAt;
}
