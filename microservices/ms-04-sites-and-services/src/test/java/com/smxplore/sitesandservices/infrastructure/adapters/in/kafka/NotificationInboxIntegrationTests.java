package com.smxplore.sitesandservices.infrastructure.adapters.in.kafka;

import com.smxplore.sitesandservices.AbstractKafkaMongoPostgresIntegrationTest;
import com.smxplore.sitesandservices.TestJwtSupport;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.PageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.notification.NotificationResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.NotificationDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@AutoConfigureWebTestClient
class NotificationInboxIntegrationTests extends AbstractKafkaMongoPostgresIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtEncoder jwtEncoder;

    @DynamicPropertySource
    static void notifications(DynamicPropertyRegistry registry) {
        registry.add("app.notifications.enabled", () -> "true");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    @Test
    void consumesReservationEventsAndServesTheNotificationInbox() throws Exception {
        UUID touristId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String event = eventJson(eventId, reservationId, touristId, providerId);

        kafkaTemplate.send(
                        "bookings.reservation.changed",
                        reservationId.toString(),
                        event)
                .get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(notifications(touristId, providerId)).hasSize(2));

        kafkaTemplate.send(
                        "bookings.reservation.changed",
                        reservationId.toString(),
                        event)
                .get(10, TimeUnit.SECONDS);

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(notifications(touristId, providerId)).hasSize(2));

        PageResponse<NotificationResponse> touristInbox = inbox(
                touristId, "TURISTA", false);
        assertThat(touristInbox.total()).isEqualTo(1);
        NotificationResponse touristNotification = touristInbox.content().getFirst();
        assertThat(touristNotification.recipientId()).isEqualTo(touristId);
        assertThat(touristNotification.type().name()).isEqualTo("RESERVATION_CONFIRMED");
        assertThat(touristNotification.readAt()).isNull();

        PageResponse<NotificationResponse> providerInbox = inbox(
                providerId, "PRESTADORA_SERVICIOS", false);
        assertThat(providerInbox.total()).isEqualTo(1);
        NotificationResponse providerNotification = providerInbox.content().getFirst();

        clientFor(touristId, "TURISTA")
                .patch().uri("/api/v1/notifications/"
                        + providerNotification.notificationId() + "/read")
                .exchange()
                .expectStatus().isForbidden();

        NotificationResponse read = clientFor(touristId, "TURISTA")
                .patch().uri("/api/v1/notifications/"
                        + touristNotification.notificationId() + "/read")
                .exchange()
                .expectStatus().isOk()
                .expectBody(NotificationResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(read).isNotNull();
        assertThat(read.readAt()).isNotNull();

        assertThat(inbox(touristId, "TURISTA", true).total()).isZero();
        assertThat(inbox(providerId, "PRESTADORA_SERVICIOS", true).total()).isEqualTo(1);
    }

    private List<NotificationDocument> notifications(UUID touristId, UUID providerId) {
        return mongoTemplate.find(
                        Query.query(Criteria.where("recipientId").in(
                                List.of(touristId.toString(), providerId.toString()))),
                        NotificationDocument.class)
                .collectList()
                .block();
    }

    private PageResponse<NotificationResponse> inbox(
            UUID userId,
            String role,
            boolean unreadOnly
    ) {
        return clientFor(userId, role)
                .get().uri("/api/v1/notifications?unreadOnly=" + unreadOnly)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<PageResponse<NotificationResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }

    private WebTestClient clientFor(UUID userId, String role) {
        return webTestClient.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + TestJwtSupport.bearer(jwtEncoder, userId, role, "Test User"))
                .build();
    }

    private String eventJson(
            UUID eventId,
            UUID reservationId,
            UUID touristId,
            UUID providerId
    ) {
        Instant now = Instant.now();
        return """
                {
                  "eventId": "%s",
                  "eventType": "bookings.reservation.changed",
                  "eventVersion": 1,
                  "occurredAt": "%s",
                  "aggregateId": "%s",
                  "correlationId": "%s",
                  "producer": "ms-04",
                  "payload": {
                    "reservationId": "%s",
                    "touristId": "%s",
                    "bookedFor": "%s",
                    "status": "CONFIRMED",
                    "items": [
                      {
                        "itemType": "SERVICE",
                        "itemId": "%s",
                        "providerId": "%s",
                        "serviceName": "Tour de prueba",
                        "people": 2,
                        "total": 100000
                      }
                    ],
                    "updatedAt": "%s"
                  }
                }
                """.formatted(
                eventId,
                now,
                reservationId,
                UUID.randomUUID(),
                reservationId,
                touristId,
                now.plus(Duration.ofDays(7)),
                UUID.randomUUID(),
                providerId,
                now
        );
    }
}
