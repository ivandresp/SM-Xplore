package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewSummaryAggregation {

    private double averageRating;
    private long reviewCount;
}
