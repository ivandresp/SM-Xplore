package com.smxplore.recommendations.infrastructure.adapters.out.quota;

import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.infrastructure.config.RecommendationQuotaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for MS05-04: a repeated view of the same item inside the dedupe window must
 * not reach the graph, so a loop over an endpoint cannot inflate {@code VIO.count} or the inferred
 * affinity of the tourist.
 */
class InMemoryInteractionDeduplicationAdapterTest {

    private static final UUID TOURIST = UUID.randomUUID();
    private static final UUID OTHER_TOURIST = UUID.randomUUID();
    private static final UUID ITEM = UUID.randomUUID();
    private static final UUID OTHER_ITEM = UUID.randomUUID();

    private RecommendationQuotaProperties properties;
    private InMemoryInteractionDeduplicationAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new RecommendationQuotaProperties();
        properties.setViewDedupeWindow(Duration.ofMinutes(1));
        properties.setMaxEntries(100);
        adapter = new InMemoryInteractionDeduplicationAdapter(properties);
    }

    @Test
    void reportsTheSameViewAsDuplicateInsideTheWindow() {
        assertThat(adapter.isDuplicate(TOURIST, ItemType.EVENT, ITEM)).isFalse();
        assertThat(adapter.isDuplicate(TOURIST, ItemType.EVENT, ITEM)).isTrue();
        assertThat(adapter.isDuplicate(TOURIST, ItemType.EVENT, ITEM)).isTrue();
    }

    @Test
    void tracksEachItemIndependently() {
        assertThat(adapter.isDuplicate(TOURIST, ItemType.EVENT, ITEM)).isFalse();

        assertThat(adapter.isDuplicate(TOURIST, ItemType.EVENT, OTHER_ITEM)).isFalse();
        assertThat(adapter.isDuplicate(TOURIST, ItemType.SITE, ITEM)).isFalse();
    }

    @Test
    void tracksEachTouristIndependently() {
        assertThat(adapter.isDuplicate(TOURIST, ItemType.EVENT, ITEM)).isFalse();

        assertThat(adapter.isDuplicate(OTHER_TOURIST, ItemType.EVENT, ITEM)).isFalse();
    }

    @Test
    void toleratesMissingValues() {
        assertThat(adapter.isDuplicate(null, null, null)).isFalse();
    }
}
