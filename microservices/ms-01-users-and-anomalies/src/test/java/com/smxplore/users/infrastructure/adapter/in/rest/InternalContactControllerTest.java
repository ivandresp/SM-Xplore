package com.smxplore.users.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.UserContactView;
import com.smxplore.users.application.port.in.GetUserContactUseCase;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.infrastructure.config.InternalApiProperties;
import com.smxplore.users.infrastructure.rest.ApiExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Pruebas del controlador interno sin levantar el contexto ni Docker: el controlador se monta solo con el
 * manejador de excepciones real, así se verifican los códigos 200, 404, 403 y 503 de la especificación.
 */
class InternalContactControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String API_KEY = "clave-interna-de-prueba";
    private static final UserContactView CONTACT =
            new UserContactView(USER_ID, "ada@example.com", "Ada Turista", "TOURIST", "3001234567", true);

    @Test
    void returnsTheContactWhenTheKeyMatches() {
        WebTestClient client = client(Mono.just(CONTACT), API_KEY);

        client.get()
                .uri(contactPath())
                .header(InternalContactController.API_KEY_HEADER, API_KEY)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.userId")
                .isEqualTo(USER_ID.toString())
                .jsonPath("$.email")
                .isEqualTo("ada@example.com")
                .jsonPath("$.fullName")
                .isEqualTo("Ada Turista")
                .jsonPath("$.role")
                .isEqualTo("TOURIST")
                .jsonPath("$.phone")
                .isEqualTo("3001234567")
                .jsonPath("$.active")
                .isEqualTo(true);
    }

    @Test
    void returnsNotFoundWhenTheUserDoesNotExist() {
        WebTestClient client = client(Mono.error(UserNotFoundException::new), API_KEY);

        client.get()
                .uri(contactPath())
                .header(InternalContactController.API_KEY_HEADER, API_KEY)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("USER_NOT_FOUND"));
    }

    @Test
    void rejectsAConfiguredKeyThatDoesNotMatch() {
        WebTestClient client = client(Mono.just(CONTACT), API_KEY);

        client.get()
                .uri(contactPath())
                .header(InternalContactController.API_KEY_HEADER, "otra-clave")
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("INVALID_INTERNAL_API_KEY"));
    }

    @Test
    void rejectsARequestWithoutTheInternalKey() {
        WebTestClient client = client(Mono.just(CONTACT), API_KEY);

        client.get()
                .uri(contactPath())
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("INVALID_INTERNAL_API_KEY"));
    }

    @Test
    void returnsServiceUnavailableWhenTheKeyIsNotConfigured() {
        WebTestClient client = client(Mono.just(CONTACT), " ");

        client.get()
                .uri(contactPath())
                .header(InternalContactController.API_KEY_HEADER, API_KEY)
                .exchange()
                .expectStatus()
                .isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("INTERNAL_API_NOT_CONFIGURED"));
    }

    private static WebTestClient client(Mono<UserContactView> result, String configuredKey) {
        GetUserContactUseCase useCase = userId -> result;
        return WebTestClient.bindToController(
                        new InternalContactController(useCase, new InternalApiProperties(configuredKey)))
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static String contactPath() {
        return "/internal/users/" + USER_ID + "/contact";
    }
}
