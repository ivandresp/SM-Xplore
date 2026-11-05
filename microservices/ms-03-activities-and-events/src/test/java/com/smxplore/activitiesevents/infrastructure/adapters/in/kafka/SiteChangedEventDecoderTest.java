package com.smxplore.activitiesevents.infrastructure.adapters.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SiteChangedEventDecoderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SiteChangedEventDecoder decoder = new SiteChangedEventDecoder(mapper);

    @Test
    void readsTheV2Payload() {
        var siteId = UUID.randomUUID();
        var managerId = UUID.randomUUID();

        var change = decoder.decode(message(2, siteId, managerId, "Playa Rosa", "2026-11-05T03:00:00Z"));

        assertThat(change).isPresent();
        assertThat(change.get().siteId()).isEqualTo(siteId);
        assertThat(change.get().managerId()).isEqualTo(managerId);
        assertThat(change.get().siteName()).isEqualTo("Playa Rosa");
        assertThat(change.get().updatedAt()).isEqualTo(Instant.parse("2026-11-05T03:00:00Z"));
    }

    @Test
    void keepsTheNameOptional() {
        var change = decoder.decode(message(2, UUID.randomUUID(), UUID.randomUUID(), null,
                "2026-11-05T03:00:00Z"));

        assertThat(change).isPresent();
        assertThat(change.get().siteName()).isNull();
    }

    @Test
    void skipsV1BecauseItHasNoManager() {
        var siteId = UUID.randomUUID();

        assertThat(decoder.decode("""
                {"eventType": "catalog.site.changed", "eventVersion": 1,
                 "payload": {"siteId": "%s"}}
                """.formatted(siteId))).isEmpty();
    }

    @Test
    void skipsAnotherEventTypeAndBlankMessages() {
        assertThat(decoder.decode("""
                {"eventType": "catalog.service.changed", "eventVersion": 2, "payload": {}}
                """)).isEmpty();
        assertThat(decoder.decode(null)).isEmpty();
        assertThat(decoder.decode("  ")).isEmpty();
    }

    @Test
    void rejectsAV2WithoutManager() {
        assertThatThrownBy(() -> decoder.decode("""
                {"eventType": "catalog.site.changed", "eventVersion": 2,
                 "payload": {"siteId": "%s", "updatedAt": "2026-11-05T03:00:00Z"}}
                """.formatted(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("managerId");
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> decoder.decode("{not json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String message(int version, UUID siteId, UUID managerId, String name, String updatedAt) {
        return """
                {"eventId": "c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e51",
                 "eventType": "catalog.site.changed",
                 "eventVersion": %d,
                 "occurredAt": "%5$s",
                 "aggregateId": "%2$s",
                 "correlationId": "9f32c4d5-e6f7-4a81-9c1b-2e3f4a5b6c7d",
                 "producer": "ms-04",
                 "payload": {"siteId": "%2$s", "name": %4$s, "categoryId": "11111111-1111-1111-1111-111111111102",
                             "managerId": "%3$s", "latitude": 10.391, "longitude": -75.479,
                             "status": "PUBLISHED", "updatedAt": "%5$s"}}
                """.formatted(version, siteId, managerId,
                name == null ? "null" : "\"" + name + "\"", updatedAt);
    }
}
