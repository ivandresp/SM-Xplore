package com.smxplore.activitiesevents.infrastructure.adapters.in.web;

import com.smxplore.activitiesevents.domain.exceptions.reservation.ReservationItemNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.TooManyReservationItemsException;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItem;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.AddReservationItemUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.AddReservationItemsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultOneReservationItemUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultOneReservationUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationItemsByTypeUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationItemsUseCasePort;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller.ReservationItemController;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.error.GlobalExceptionHandler;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper.ReservationItemWebMapperImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@WebFluxTest(controllers = ReservationItemController.class)
@Import({ReservationItemWebMapperImpl.class, GlobalExceptionHandler.class})
class ReservationItemControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AddReservationItemUseCasePort addReservationItemUseCase;

    @MockitoBean
    private AddReservationItemsUseCasePort addReservationItemsUseCase;

    @MockitoBean
    private ConsultReservationItemsUseCasePort consultReservationItemsUseCase;

    @MockitoBean
    private ConsultOneReservationItemUseCasePort consultOneReservationItemUseCase;

    @MockitoBean
    private ConsultReservationItemsByTypeUseCasePort consultReservationItemsByTypeUseCase;

    @MockitoBean
    private ConsultOneReservationUseCasePort consultOneReservationUseCase;

    @Autowired
    void configureClient(WebTestClient webTestClient) {
        this.webTestClient = webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(jwt -> jwt.subject("tester").claim("role", "ADMIN")))
                .mutateWith(SecurityMockServerConfigurers.csrf());
    }

    @Test
    void addsOneItemToTheReservationOfThePath() {
        var reservationId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        var saved = item(reservationId, ReservationItemType.ACTIVITY, itemId);
        given(addReservationItemUseCase.handle(eq(reservationId), argThat(item ->
                itemId.equals(item.getItemId()) && item.getType() == ReservationItemType.ACTIVITY)))
                .willReturn(Mono.just(saved));

        webTestClient.post().uri("/api/v1/reservations/{reservationId}/items", reservationId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"itemId": "%s", "type": "ACTIVITY", "price": 25000.00}
                        """.formatted(itemId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(saved.getId().toString())
                .jsonPath("$.reservationId").isEqualTo(reservationId.toString())
                .jsonPath("$.itemId").isEqualTo(itemId.toString())
                .jsonPath("$.price").isEqualTo(25000.00);
    }

    @Test
    void addsSeveralItemsInBatch() {
        var reservationId = UUID.randomUUID();
        var saved = List.of(
                item(reservationId, ReservationItemType.ACTIVITY, UUID.randomUUID()),
                item(reservationId, ReservationItemType.ACTIVITY, UUID.randomUUID()));
        given(addReservationItemsUseCase.handle(eq(reservationId), any())).willReturn(Flux.fromIterable(saved));

        webTestClient.post().uri("/api/v1/reservations/{reservationId}/items/batch", reservationId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "items": [
                            {"itemId": "%s", "type": "ACTIVITY", "price": 10000.00},
                            {"itemId": "%s", "type": "ACTIVITY", "price": 20000.00}
                          ]
                        }
                        """.formatted(UUID.randomUUID(), UUID.randomUUID()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].reservationId").isEqualTo(reservationId.toString());
    }

    @Test
    void addItemReturnsConflictWhenTheBusinessRuleIsViolated() {
        var reservationId = UUID.randomUUID();
        given(addReservationItemUseCase.handle(eq(reservationId), any()))
                .willReturn(Mono.error(new TooManyReservationItemsException(
                        "An EVENT reservation can only have one item.")));

        webTestClient.post().uri("/api/v1/reservations/{reservationId}/items", reservationId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"itemId": "%s", "type": "EVENT", "price": 50000.00}
                        """.formatted(UUID.randomUUID()))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.status").isEqualTo(409);
    }

    @Test
    void listsTheItemsOfAReservation() {
        var reservationId = UUID.randomUUID();
        given(consultReservationItemsUseCase.handle(reservationId))
                .willReturn(Flux.just(item(reservationId, ReservationItemType.ACTIVITY, UUID.randomUUID())));

        webTestClient.get().uri("/api/v1/reservations/{reservationId}/items", reservationId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].reservationId").isEqualTo(reservationId.toString());
    }

    @Test
    void findOneReturnsNotFoundWhenTheItemDoesNotExist() {
        var reservationItemId = UUID.randomUUID();
        given(consultOneReservationItemUseCase.handle(reservationItemId))
                .willReturn(Mono.error(new ReservationItemNotFoundException(reservationItemId)));

        webTestClient.get().uri("/api/v1/reservation-items/{reservationItemId}", reservationItemId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404);
    }

    @Test
    void findsTheItemsByType() {
        var item = item(UUID.randomUUID(), ReservationItemType.ACTIVITY, UUID.randomUUID());
        given(consultReservationItemsByTypeUseCase.handle(ReservationItemType.ACTIVITY, 50))
                .willReturn(Flux.just(item));

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/reservation-items")
                        .queryParam("type", "ACTIVITY")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].itemId").isEqualTo(item.getItemId().toString());
    }

    private ReservationItem item(UUID reservationId, ReservationItemType type, UUID itemId) {
        return ReservationItem.builder()
                .reservationId(reservationId)
                .itemId(itemId)
                .type(type)
                .price(new BigDecimal("25000.00"))
                .build();
    }
}
