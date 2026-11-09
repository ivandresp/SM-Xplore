package com.smxplore.recommendations.domain.model.event;

import java.util.List;
import java.util.Set;

/**
 * Kafka topics consumed by ms-05. The event type and the topic have the same name.
 * bookings.reservation.changed is deliberately absent: ms-05 does not use reservations.
 */
public record EventTopics() {

    public static final String CATALOG_SITE_CHANGED = "catalog.site.changed";
    public static final String CATALOG_SERVICE_CHANGED = "catalog.service.changed";
    public static final String CATALOG_CATEGORY_CHANGED = "catalog.category.changed";
    /** Zones are enriched by ms-04; ms-05 only keeps the reference for the site graph. */
    public static final String CATALOG_ZONE_CHANGED = "catalog.zone.changed";
    public static final String ACTIVITIES_EVENT_CHANGED = "activities.event.changed";
    public static final String ACTIVITIES_ACTIVITY_CHANGED = "activities.activity.changed";
    public static final String REVIEWS_REVIEW_CHANGED = "reviews.review.changed";

    public static final String BOOKINGS_RESERVATION_CHANGED = "bookings.reservation.changed";

    public static final List<String> CONSUMED = List.of(
            CATALOG_SITE_CHANGED,
            CATALOG_SERVICE_CHANGED,
            CATALOG_CATEGORY_CHANGED,
            CATALOG_ZONE_CHANGED,
            ACTIVITIES_EVENT_CHANGED,
            ACTIVITIES_ACTIVITY_CHANGED,
            REVIEWS_REVIEW_CHANGED
    );

    public static final Set<String> CONSUMED_SET = Set.copyOf(CONSUMED);

    public static boolean isConsumed(String topic) {
        return CONSUMED_SET.contains(topic);
    }

    private static final Set<String> CATALOG_PRODUCERS = Set.of("ms-04");
    /** ms-03 firma sus eventos con su constante PRODUCER, no con "ms-03". */
    private static final Set<String> ACTIVITIES_PRODUCERS = Set.of("ms-03-activities-and-events");
    /**
     * reviews.review.changed has two legitimate producers by contract: ms-03 publishes the
     * reviews of activities and events, and ms-04 publishes the reviews of sites and services.
     */
    private static final Set<String> REVIEWS_PRODUCERS =
            Set.of("ms-03-activities-and-events", "ms-04");

    /**
     * Producers that may publish a consumed topic. The projection only accepts events whose
     * {@code producer} metadata is one of these values: ms-04 publishes the catalog (sites,
     * services, categories and zones) and ms-03 publishes activities and events, while
     * reviews.review.changed is shared by ms-03 and ms-04.
     *
     * <p>The producer field is self-declared, so this is a consistency check and not a
     * signature. It stops a misconfigured or rogue publisher inside the stack from
     * attributing events to another service, and it is the first line of defence while
     * the broker has no authentication (MS05-01).</p>
     *
     * @return expected producers, or an empty set when the topic is not consumed
     */
    public static Set<String> expectedProducers(String topic) {
        return switch (topic) {
            case CATALOG_SITE_CHANGED, CATALOG_SERVICE_CHANGED,
                    CATALOG_CATEGORY_CHANGED, CATALOG_ZONE_CHANGED -> CATALOG_PRODUCERS;
            case ACTIVITIES_EVENT_CHANGED, ACTIVITIES_ACTIVITY_CHANGED -> ACTIVITIES_PRODUCERS;
            case REVIEWS_REVIEW_CHANGED -> REVIEWS_PRODUCERS;
            default -> Set.of();
        };
    }
}
