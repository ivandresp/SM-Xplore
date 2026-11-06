package com.smxplore.recommendations.domain.model.catalog;

/**
 * Catalog groups that can be reindexed.
 *
 * <p>{@link #CATEGORIES}, {@link #SITES} and {@link #SERVICES} are exported by ms-04;
 * {@link #ACTIVITIES} and {@link #EVENTS} are exported by ms-03. The reindex client
 * resolves the base URL per entity, so both producers are supported by the same endpoint.</p>
 */
public enum CatalogEntity {
    CATEGORIES,
    SITES,
    SERVICES,
    ACTIVITIES,
    EVENTS
}
