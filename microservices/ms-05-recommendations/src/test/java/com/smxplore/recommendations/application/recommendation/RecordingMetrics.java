package com.smxplore.recommendations.application.recommendation;

import com.smxplore.recommendations.domain.model.recommendation.Recommendation;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationItem;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationSource;
import com.smxplore.recommendations.domain.ports.out.RecommendationMetricsPort;

import java.util.ArrayList;
import java.util.List;

final class RecordingMetrics implements RecommendationMetricsPort {

    private int cacheHits;
    private int cacheMisses;
    private int adminCrossTouristAccess;
    private final List<RecommendationSource> sources = new ArrayList<>();
    private final List<Integer> items = new ArrayList<>();
    private final List<Integer> categories = new ArrayList<>();
    private final List<Integer> sites = new ArrayList<>();

    @Override
    public void served(RecommendationSource source, int itemCount) {
        sources.add(source);
        items.add(itemCount);
    }

    @Override
    public void cacheHit() {
        cacheHits++;
    }

    @Override
    public void cacheMiss() {
        cacheMisses++;
    }

    @Override
    public void diversity(int distinctCategories, int distinctSites) {
        categories.add(distinctCategories);
        sites.add(distinctSites);
    }

    @Override
    public void adminCrossTouristAccess() {
        adminCrossTouristAccess++;
    }

    int adminCrossTouristAccessCount() {
        return adminCrossTouristAccess;
    }

    int cacheHits() {
        return cacheHits;
    }

    int cacheMisses() {
        return cacheMisses;
    }

    List<RecommendationSource> sources() {
        return sources;
    }

    List<Integer> items() {
        return items;
    }

    List<Integer> categories() {
        return categories;
    }

    List<Integer> sites() {
        return sites;
    }

    static int distinctCategories(Recommendation recommendation) {
        return (int) recommendation.items().stream()
                .map(RecommendationItem::categoryId)
                .distinct()
                .count();
    }
}
