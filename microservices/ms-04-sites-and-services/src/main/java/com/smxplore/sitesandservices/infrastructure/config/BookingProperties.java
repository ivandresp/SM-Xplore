package com.smxplore.sitesandservices.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.bookings")
public class BookingProperties {

    private String zoneId = "America/Bogota";
    private int maxActivePerTourist = 5;
    private int maxCreationsPerMinute = 10;
    private int maxAdvanceDays = 365;
    private boolean autoCompleteEnabled = true;
    private long completionIntervalMs = 60_000;
    private int completionBatchSize = 100;

    @PostConstruct
    public void validate() {
        validateZoneId();
        validatePositive("max-active-per-tourist", maxActivePerTourist);
        validatePositive("max-creations-per-minute", maxCreationsPerMinute);
        validatePositive("max-advance-days", maxAdvanceDays);
        validatePositive("completion-interval-ms", completionIntervalMs);
        validatePositive("completion-batch-size", completionBatchSize);
    }

    private void validateZoneId() {
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalStateException("app.bookings.zone-id is required");
        }
        try {
            ZoneId.of(zoneId);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "app.bookings.zone-id is not a valid time zone: " + zoneId, exception);
        }
    }

    private void validatePositive(String name, long value) {
        if (value < 1) {
            throw new IllegalStateException("app.bookings." + name + " must be >= 1");
        }
    }

    public ZoneId zone() {
        return ZoneId.of(zoneId);
    }
}
