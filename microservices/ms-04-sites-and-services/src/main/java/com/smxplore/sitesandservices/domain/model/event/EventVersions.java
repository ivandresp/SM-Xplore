package com.smxplore.sitesandservices.domain.model.event;

/**
 * Version of the payload each event type publishes today.
 *
 * <p>Single source of truth for the Kafka envelope and for the catalog export feed of ms-05: the
 * export used to leave the version out, so ms-05 validated a v2 site payload as v1 and could not
 * tell whether {@code managerId} was required.
 *
 * <p>These are the versions <strong>ms-04</strong> publishes. The same event type can have another
 * version from another producer ({@code bookings.reservation.changed} is v2 in ms-03); the version
 * is a property of the producer, not of the type.
 */
public final class EventVersions {

    public static final int CATALOG_CATEGORY_CHANGED = 1;
    public static final int CATALOG_ZONE_CHANGED = 1;
    /** v2 adds {@code managerId}. */
    public static final int CATALOG_SITE_CHANGED = 2;
    public static final int CATALOG_SERVICE_CHANGED = 1;
    public static final int REVIEWS_REVIEW_CHANGED = 1;
    /** ms-04 publishes the service reservations in v1; ms-03 publishes its own in v2. */
    public static final int BOOKINGS_RESERVATION_CHANGED = 1;

    private EventVersions() {
    }

    public static int of(String eventType) {
        return switch (eventType) {
            case EventTypes.CATALOG_CATEGORY_CHANGED -> CATALOG_CATEGORY_CHANGED;
            case EventTypes.CATALOG_ZONE_CHANGED -> CATALOG_ZONE_CHANGED;
            case EventTypes.CATALOG_SITE_CHANGED -> CATALOG_SITE_CHANGED;
            case EventTypes.CATALOG_SERVICE_CHANGED -> CATALOG_SERVICE_CHANGED;
            case EventTypes.REVIEWS_REVIEW_CHANGED -> REVIEWS_REVIEW_CHANGED;
            case EventTypes.BOOKINGS_RESERVATION_CHANGED -> BOOKINGS_RESERVATION_CHANGED;
            default -> 1;
        };
    }
}
