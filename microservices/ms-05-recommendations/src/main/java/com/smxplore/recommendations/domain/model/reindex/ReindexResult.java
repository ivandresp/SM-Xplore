package com.smxplore.recommendations.domain.model.reindex;

import com.smxplore.recommendations.domain.exception.InvalidReindexException;
import com.smxplore.recommendations.domain.model.catalog.CatalogEntity;

/**
 * Outcome of a reindex run. {@code events} is the sum of every page and must match the sum of the
 * four counters; the split tells the operator whether the run actually changed the graph.
 */
public record ReindexResult(CatalogEntity entity, int pages, int events, int applied, int duplicate, int stale) {

    public ReindexResult {
        if (entity == null) {
            throw new InvalidReindexException("entity is required");
        }
        if (pages < 0 || events < 0 || applied < 0 || duplicate < 0 || stale < 0) {
            throw new InvalidReindexException("reindex counters cannot be negative");
        }
        if (events != applied + duplicate + stale) {
            throw new InvalidReindexException("reindex counters must add up to the number of events");
        }
    }
}
