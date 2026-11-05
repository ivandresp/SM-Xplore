package com.smxplore.recommendations.infrastructure.adapters.out.metrics;

import com.smxplore.recommendations.domain.model.recommendation.RecommendationSource;
import com.smxplore.recommendations.domain.ports.out.RecommendationMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.EnumMap;
import java.util.Map;

/**
 * Micrometer adapter: counters per source, cache results and the size of the diversity selection.
 */
public class MicrometerRecommendationMetrics implements RecommendationMetricsPort {

    private final MeterRegistry registry;

    static final String SERVED = "recommendations.served";
    static final String CACHE = "recommendations.cache";
    static final String DIVERSITY_CATEGORIES = "recommendations.diversity.categories";
    static final String DIVERSITY_SITES = "recommendations.diversity.sites";
    static final String ITEMS = "recommendations.items";

    private final Map<RecommendationSource, Counter> servedBySource = new EnumMap<>(RecommendationSource.class);
    private final DistributionSummary items;
    private final DistributionSummary categories;
    private final DistributionSummary sites;
    private Counter cacheHits;
    private Counter cacheMisses;

    public MicrometerRecommendationMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.items = DistributionSummary.builder(ITEMS)
                .description("Elementos devueltos por cada recomendacion")
                .register(registry);
        this.categories = DistributionSummary.builder(DIVERSITY_CATEGORIES)
                .description("Categorias distintas por recomendacion")
                .register(registry);
        this.sites = DistributionSummary.builder(DIVERSITY_SITES)
                .description("Sitios distintos por recomendacion")
                .register(registry);
    }

    @Override
    public void served(RecommendationSource source, int itemCount) {
        if (source == null) {
            return;
        }
        servedBySource.computeIfAbsent(source, value -> Counter.builder(SERVED)
                        .description("Recomendaciones servidas por origen")
                        .tag("source", value.name())
                        .register(registry))
                .increment();
        items.record(itemCount);
    }

    @Override
    public void cacheHit() {
        if (cacheHits == null) {
            cacheHits = Counter.builder(CACHE)
                    .description("Consultas resueltas por la cache")
                    .tag("result", "hit")
                    .register(registry);
        }
        cacheHits.increment();
    }

    @Override
    public void cacheMiss() {
        if (cacheMisses == null) {
            cacheMisses = Counter.builder(CACHE)
                    .description("Consultas que hubo que calcular")
                    .tag("result", "miss")
                    .register(registry);
        }
        cacheMisses.increment();
    }

    @Override
    public void diversity(int distinctCategories, int distinctSites) {
        categories.record(distinctCategories);
        sites.record(distinctSites);
    }
}
