package com.smxplore.sitesandservices.infrastructure.adapters.out.outbox;

public record OutboxReprocessResult(
        long reservationEvents,
        long catalogEvents
) {
}
