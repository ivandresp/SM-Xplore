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
    public static final String ACTIVITIES_EVENT_CHANGED = "activities.event.changed";
    public static final String ACTIVITIES_ACTIVITY_CHANGED = "activities.activity.changed";
    public static final String REVIEWS_REVIEW_CHANGED = "reviews.review.changed";

    public static final String BOOKINGS_RESERVATION_CHANGED = "bookings.reservation.changed";

    public static final List<String> CONSUMED = List.of(
            CATALOG_SITE_CHANGED,
            CATALOG_SERVICE_CHANGED,
            CATALOG_CATEGORY_CHANGED,
            ACTIVITIES_EVENT_CHANGED,
            ACTIVITIES_ACTIVITY_CHANGED,
            REVIEWS_REVIEW_CHANGED
    );

    public static final Set<String> CONSUMED_SET = Set.copyOf(CONSUMED);

    public static boolean isConsumed(String topic) {
        return CONSUMED_SET.contains(topic);
    }
}
