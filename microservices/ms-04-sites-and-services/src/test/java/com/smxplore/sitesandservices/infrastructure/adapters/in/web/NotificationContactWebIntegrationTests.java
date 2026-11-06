package com.smxplore.sitesandservices.infrastructure.adapters.in.web;

import com.smxplore.sitesandservices.AbstractMongoPostgresIntegrationTest;
import com.smxplore.sitesandservices.TestJwtSupport;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.notification.NotificationContactResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.NotificationContactDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWebTestClient
class NotificationContactWebIntegrationTests extends AbstractMongoPostgresIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    @Test
    void registersUpdatesAndRemovesTheEmailOfARecipient() {
        UUID adminId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        NotificationContactResponse created = clientFor(adminId, "ADMIN")
                .put().uri("/api/v1/admin/notification-contacts/" + recipientId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"tourist@example.com"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(NotificationContactResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(created).isNotNull();
        assertThat(created.userId()).isEqualTo(recipientId);
        assertThat(created.email()).isEqualTo("tourist@example.com");
        assertThat(created.updatedAt()).isNotNull();
        assertThat(stored(recipientId).getEmail()).isEqualTo("tourist@example.com");

        NotificationContactResponse updated = clientFor(adminId, "ADMIN")
                .put().uri("/api/v1/admin/notification-contacts/" + recipientId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"  tourist+updated@example.com  "}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(NotificationContactResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(updated).isNotNull();
        assertThat(updated.email()).isEqualTo("tourist+updated@example.com");
        assertThat(stored(recipientId).getEmail()).isEqualTo("tourist+updated@example.com");

        clientFor(adminId, "ADMIN")
                .delete().uri("/api/v1/admin/notification-contacts/" + recipientId)
                .exchange()
                .expectStatus().isNoContent();
        assertThat(stored(recipientId)).isNull();
    }

    @Test
    void onlyAdministratorsCanManageContacts() {
        UUID recipientId = UUID.randomUUID();

        clientFor(UUID.randomUUID(), "TOURIST")
                .put().uri("/api/v1/admin/notification-contacts/" + recipientId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"tourist@example.com"}
                        """)
                .exchange()
                .expectStatus().isForbidden();

        clientFor(UUID.randomUUID(), "DESTINATION_MANAGER")
                .delete().uri("/api/v1/admin/notification-contacts/" + recipientId)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void rejectsInvalidEmailAddresses() {
        clientFor(UUID.randomUUID(), "ADMIN")
                .put().uri("/api/v1/admin/notification-contacts/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"not-an-email"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    private NotificationContactDocument stored(UUID userId) {
        return mongoTemplate.findById(userId.toString(), NotificationContactDocument.class).block();
    }

    private WebTestClient clientFor(UUID userId, String role) {
        return webTestClient.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + TestJwtSupport.bearer(jwtEncoder, userId, role, "Test User"))
                .build();
    }
}
