package com.smxplore.sitesandservices.application.security;

import com.smxplore.sitesandservices.domain.exception.InvalidNotificationEventException;
import com.smxplore.sitesandservices.infrastructure.adapters.in.kafka.ReservationChangedEventDecoder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MS04-11: el consumidor de Kafka solo acepta eventos de productores y versiones conocidas y con
 * un tamano acotado.
 */
class ReservationEventProvenanceTest {

    private static final String EVENT_ID = "11111111-1111-1111-1111-111111111101";
    private static final String RESERVATION_ID = "22222222-2222-2222-2222-222222222201";
    private static final String TOURIST_ID = "33333333-3333-3333-3333-333333333301";
    private static final String ITEM_ID = "44444444-4444-4444-4444-444444444401";

    private final ReservationChangedEventDecoder decoder =
            new ReservationChangedEventDecoder(new ObjectMapper());

    @Test
    void acceptsAnEventFromAKnownProducer() {
        assertThat(decoder.decode(event("ms-03", "1", 1))).isPresent();
        assertThat(decoder.decode(event("ms-04", null, 1))).isPresent();
    }

    @Test
    void rejectsAnUnknownProducer() {
        assertThatThrownBy(() -> decoder.decode(event("evil-service", "1", 1)))
                .isInstanceOf(InvalidNotificationEventException.class)
                .hasMessageContaining("Unexpected booking event producer");
    }

    @Test
    void rejectsAMissingProducer() {
        String json = event("ms-03", "1", 1).replace(",\"producer\":\"ms-03\"", "");
        assertThatThrownBy(() -> decoder.decode(json))
                .isInstanceOf(InvalidNotificationEventException.class)
                .hasMessageContaining("producer is required");
    }

    @Test
    void rejectsAnUnsupportedVersion() {
        assertThatThrownBy(() -> decoder.decode(event("ms-03", "99", 1)))
                .isInstanceOf(InvalidNotificationEventException.class)
                .hasMessageContaining("Unsupported booking event version");
    }

    @Test
    void rejectsEventsWithTooManyItems() {
        StringBuilder json = new StringBuilder(event("ms-03", "1", 0));
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 51; i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("""
                    {"itemType":"EVENT","itemId":"%s","itemName":"Evento","people":1}"""
                    .formatted(ITEM_ID));
        }
        json.replace(json.indexOf("\"items\":[") + "\"items\":[".length(),
                json.indexOf("]"), items.toString());

        assertThatThrownBy(() -> decoder.decode(json.toString()))
                .isInstanceOf(InvalidNotificationEventException.class)
                .hasMessageContaining("more than 50 items");
    }

    private static String event(String producer, String version, int items) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < items; i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append("""
                    {"itemType":"EVENT","itemId":"%s","itemName":"Evento","people":2}"""
                    .formatted(ITEM_ID));
        }
        String producerField = producer == null ? "" : ",\"producer\":\"" + producer + "\"";
        String versionField = version == null ? "" : ",\"eventVersion\":" + version;
        return """
                {"eventType":"bookings.reservation.changed","eventId":"%s"%s%s,
                 "payload":{"reservationId":"%s","touristId":"%s","status":"CONFIRMED",
                 "items":[%s]}}"""
                .formatted(EVENT_ID, producerField, versionField, RESERVATION_ID, TOURIST_ID, body);
    }
}
