package com.smxplore.recommendations.domain.service;

import com.smxplore.recommendations.domain.model.RecommendationPolicy;
import com.smxplore.recommendations.domain.model.ScoredCandidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Applies exposure caps after scoring. The first pass enforces site/category
 * caps; a second pass fills the remaining slots from the best candidates if
 * the catalog is too small to satisfy every cap.
 */
public class DiversitySelector {

    private final RecommendationPolicy policy;

    public DiversitySelector(RecommendationPolicy policy) {
        this.policy = policy;
    }

    public List<ScoredCandidate> select(List<ScoredCandidate> ranked, int limit) {
        if (ranked.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<ScoredCandidate> sorted = ranked.stream()
                .sorted(Comparator.comparingDouble(ScoredCandidate::score)
                        .reversed()
                        .thenComparing(scored -> scored.candidate().itemId()))
                .toList();

        int maxPerCategory = Math.max(1, (int) Math.ceil(limit * policy.maxCategoryRatio()));

        List<ScoredCandidate> selected = new ArrayList<>(limit);
        Set<UUID> selectedIds = new HashSet<>();
        Map<UUID, Integer> siteCounts = new HashMap<>();
        Map<UUID, Integer> categoryCounts = new HashMap<>();

        for (ScoredCandidate scored : sorted) {
            if (selected.size() >= limit) {
                break;
            }
            UUID siteId = scored.candidate().siteId();
            UUID categoryId = scored.candidate().categoryId();

            if (siteId != null && siteCounts.getOrDefault(siteId, 0) >= policy.maxItemsPerSite()) {
                continue;
            }
            if (categoryId != null && categoryCounts.getOrDefault(categoryId, 0) >= maxPerCategory) {
                continue;
            }

            selected.add(scored);
            selectedIds.add(scored.candidate().itemId());
            if (siteId != null) {
                siteCounts.merge(siteId, 1, Integer::sum);
            }
            if (categoryId != null) {
                categoryCounts.merge(categoryId, 1, Integer::sum);
            }
        }

        if (selected.size() < limit) {
            for (ScoredCandidate scored : sorted) {
                if (selected.size() >= limit) {
                    break;
                }
                if (selectedIds.add(scored.candidate().itemId())) {
                    selected.add(scored);
                }
            }
        }

        return List.copyOf(selected);
    }
}
