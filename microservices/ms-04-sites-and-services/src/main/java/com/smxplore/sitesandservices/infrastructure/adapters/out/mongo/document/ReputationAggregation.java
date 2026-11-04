package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class ReputationAggregation {

    private double averageRating;
    private long reviewCount;
    private long rating1;
    private long rating2;
    private long rating3;
    private long rating4;
    private long rating5;
    private Instant lastReviewAt;
}
