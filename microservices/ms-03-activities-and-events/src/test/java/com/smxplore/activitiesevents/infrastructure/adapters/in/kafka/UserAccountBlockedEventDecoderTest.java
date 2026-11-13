package com.smxplore.activitiesevents.infrastructure.adapters.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAccountBlockedEventDecoderTest {

    private static final UUID GUIDE = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final UserAccountBlockedEventDecoder decoder =
            new UserAccountBlockedEventDecoder(new ObjectMapper());

    @Test
    void decodesAGuideBlockedEvent() {
        var message = """
                {"eventId":"11111111-1111-1111-1111-111111111111","eventType":"users.account.blocked",
                 "eventVersion":1,"occurredAt":"2026-01-01T10:00:00Z","aggregateId":"44444444-4444-4444-4444-444444444444",
                 "correlationId":"22222222-2222-2222-2222-222222222222","producer":"ms-01",
                 "payload":{"userId":"44444444-4444-4444-4444-444444444444","role":"GUIDE"}}
                """;

        assertThat(decoder.decode(message)).contains(GUIDE);
    }

    @Test
    void ignoresOtherRoles() {
        var message = """
                {"eventType":"users.account.blocked","eventVersion":1,"producer":"ms-01",
                 "payload":{"userId":"44444444-4444-4444-4444-444444444444","role":"SERVICE_PROVIDER"}}
                """;

        assertThat(decoder.decode(message)).isEmpty();
    }

    @Test
    void ignoresOtherProducers() {
        var message = """
                {"eventType":"users.account.blocked","eventVersion":1,"producer":"ms-04",
                 "payload":{"userId":"44444444-4444-4444-4444-444444444444","role":"GUIDE"}}
                """;

        assertThat(decoder.decode(message)).isEmpty();
    }
}
