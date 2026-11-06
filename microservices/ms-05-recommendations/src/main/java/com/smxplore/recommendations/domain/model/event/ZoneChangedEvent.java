package com.smxplore.recommendations.domain.model.event;

import com.smxplore.recommendations.domain.model.catalog.ZoneStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A zone (the geographic/operational grouping owned by ms-04) changed.
 *
 * <p>ms-05 only needs the identity and the descriptive fields: the graph links a
 * {@link SiteChangedEvent site} to its zone so recommendations can be reasoned about per zone.
 * {@code type} and {@code description} are optional in the contract.</p>
 */
public record ZoneChangedEvent(
        EventMetadata metadata,
        UUID zoneId,
        UUID managerId,
        String name,
        String type,
        String description,
        ZoneStatus status,
        Instant updatedAt
) implements ProjectedEvent {

    public ZoneChangedEvent {
        EventValidation.metadata(metadata);
        EventValidation.uuid(zoneId, "zoneId");
        EventValidation.uuid(managerId, "managerId");
        EventValidation.text(name, "zone name");
        EventValidation.optionalText(type, "zone type");
        EventValidation.optionalText(description, "zone description");
        EventValidation.required(status, "status");
        EventValidation.instant(updatedAt, "updatedAt");
    }
}
