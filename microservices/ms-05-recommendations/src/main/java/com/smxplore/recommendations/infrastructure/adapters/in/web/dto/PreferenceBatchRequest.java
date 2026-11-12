package com.smxplore.recommendations.infrastructure.adapters.in.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Body of {@code PUT /api/v1/preferences}: the complete new set of explicit categories (MS05-11).
 * An empty list clears the explicit preferences while leaving inferred ones untouched.
 */
public record PreferenceBatchRequest(
        List<UUID> categoryIds
) {
}
