package com.smxplore.users.domain.model;

import java.util.Map;

public record ReputationSummary(long reviewCount, double averageRating, Map<Integer, Long> distribution) {

    public ReputationSummary {
        distribution = Map.copyOf(distribution);
    }

    public static ReputationSummary empty() {
        return new ReputationSummary(0, 0, Map.of());
    }
}
