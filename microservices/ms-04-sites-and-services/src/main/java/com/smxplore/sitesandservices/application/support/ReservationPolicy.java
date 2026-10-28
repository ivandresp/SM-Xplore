package com.smxplore.sitesandservices.application.support;

public record ReservationPolicy(
        int maxActivePerTourist,
        int maxCreationsPerMinute,
        int maxAdvanceDays
) {
}
