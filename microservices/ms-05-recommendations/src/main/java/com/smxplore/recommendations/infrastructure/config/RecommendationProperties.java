package com.smxplore.recommendations.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.recommendations")
public class RecommendationProperties {

    private int defaultLimit = 20;
    private int maxLimit = 50;
    private int candidateLimit = 200;
    private int categoryMaxDepth = 2;
    private double proximityRadiusKm = 10.0;
    private double noveltyHalfLifeDays = 90.0;
    private double preferenceHalfLifeDays = 90.0;
    private double qualityPriorMean = 3.5;
    private double qualityPriorCount = 10.0;
    private int maxItemsPerSite = 2;
    private double maxCategoryRatio = 0.4;
    private Weights weights = new Weights();

    @Getter
    @Setter
    public static class Weights {
        private double affinity = 0.45;
        private double quality = 0.25;
        private double proximity = 0.20;
        private double novelty = 0.10;
    }
}
