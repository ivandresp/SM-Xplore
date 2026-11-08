package com.smxplore.recommendations.domain.ports.out;

import com.smxplore.recommendations.domain.model.catalog.ItemType;

import java.util.UUID;

/**
 * Suppresses repeated view writes for the same tourist and item inside a short window
 * (MS05-04). A duplicate view is not an error: the API answers 204 and the graph is not
 * touched, so a client that re-renders the same card cannot inflate {@code VIO} counts or
 * trigger an extra inferred preference.
 *
 * <p>The check is synchronous and in-memory: state is local to the instance.</p>
 */
public interface InteractionDeduplicationPort {

    /**
     * Marks the view as seen and returns whether it was already seen inside the window.
     *
     * @return {@code true} when the view is a duplicate and must be skipped
     */
    boolean isDuplicate(UUID touristId, ItemType itemType, UUID itemId);
}
