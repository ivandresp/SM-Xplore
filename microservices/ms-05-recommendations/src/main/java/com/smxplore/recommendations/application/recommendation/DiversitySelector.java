package com.smxplore.recommendations.application.recommendation;

import com.smxplore.recommendations.domain.exception.InvalidLimitException;
import com.smxplore.recommendations.domain.exception.InvalidScoringException;
import com.smxplore.recommendations.domain.model.ranking.RecommendationPolicy;
import com.smxplore.recommendations.domain.model.ranking.ScoredCandidate;
import lombok.RequiredArgsConstructor;

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
@RequiredArgsConstructor
public class DiversitySelector {

    private final RecommendationPolicy policy;

    public List<ScoredCandidate> select(List<ScoredCandidate> ranked, int limit) {
        validateInput(ranked, limit);
        List<ScoredCandidate> sorted = sortByScore(ranked);
        Selection selection = selectWithCaps(sorted, limit);
        fillRemainingSlots(selection, sorted);
        return selection.items();
    }

    private List<ScoredCandidate> sortByScore(List<ScoredCandidate> ranked) {
        return ranked.stream()
                .sorted(Comparator.comparingDouble(ScoredCandidate::score)
                        .reversed()
                        .thenComparing(scored -> scored.candidate().itemId()))
                .toList();
    }

    private Selection selectWithCaps(List<ScoredCandidate> sorted, int limit) {
        int maxPerCategory = calculateMaxPerCategory(limit);
        Selection selection = new Selection(limit);
        sorted.forEach(scored -> selectIfAllowed(scored, selection, maxPerCategory));
        return selection;
    }

    private void selectIfAllowed(ScoredCandidate scored, Selection selection, int maxPerCategory) {
        if (selection.isFull()) {
            return;
        }
        if (!hasSiteCapacity(scored, selection) || !hasCategoryCapacity(scored, selection, maxPerCategory)) {
            return;
        }
        selection.add(scored);
    }

    private boolean hasSiteCapacity(ScoredCandidate scored, Selection selection) {
        UUID siteId = scored.candidate().siteId();
        return siteId == null || selection.siteCount(siteId) < policy.maxItemsPerSite();
    }

    private boolean hasCategoryCapacity(ScoredCandidate scored, Selection selection, int maxPerCategory) {
        UUID categoryId = scored.candidate().categoryId();
        return categoryId == null || selection.categoryCount(categoryId) < maxPerCategory;
    }

    private void fillRemainingSlots(Selection selection, List<ScoredCandidate> sorted) {
        sorted.forEach(selection::addIfAbsent);
    }

    private int calculateMaxPerCategory(int limit) {
        return Math.max(1, (int) Math.ceil(limit * policy.maxCategoryRatio()));
    }

    private void validateInput(List<ScoredCandidate> ranked, int limit) {
        if (ranked == null) {
            throw new InvalidScoringException("ranked candidates are required");
        }
        if (limit < 1) {
            throw new InvalidLimitException("limit must be >= 1");
        }
    }

    private static final class Selection {

        private final int limit;
        private final List<ScoredCandidate> selected = new ArrayList<>();
        private final Set<UUID> selectedIds = new HashSet<>();
        private final Map<UUID, Integer> siteCounts = new HashMap<>();
        private final Map<UUID, Integer> categoryCounts = new HashMap<>();

        private Selection(int limit) {
            this.limit = limit;
        }

        private boolean isFull() {
            return selected.size() >= limit;
        }

        private int siteCount(UUID siteId) {
            return siteCounts.getOrDefault(siteId, 0);
        }

        private int categoryCount(UUID categoryId) {
            return categoryCounts.getOrDefault(categoryId, 0);
        }

        private void add(ScoredCandidate scored) {
            selected.add(scored);
            selectedIds.add(scored.candidate().itemId());
            increment(siteCounts, scored.candidate().siteId());
            increment(categoryCounts, scored.candidate().categoryId());
        }

        private void addIfAbsent(ScoredCandidate scored) {
            if (isFull()) {
                return;
            }
            if (selectedIds.add(scored.candidate().itemId())) {
                selected.add(scored);
            }
        }

        private List<ScoredCandidate> items() {
            return List.copyOf(selected);
        }

        private static void increment(Map<UUID, Integer> counts, UUID key) {
            if (key != null) {
                counts.merge(key, 1, Integer::sum);
            }
        }
    }
}
