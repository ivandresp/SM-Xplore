package com.smxplore.activitiesevents.infrastructure.adapters.in.web;

import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventNameException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventStatusTransitionException;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.ports.usecases.event.ChangeEventStatusUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.ConsultEventsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.EstimateEventAttendanceUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.RegisterEventUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.SearchEventsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.ViewEventDetailsUseCasePort;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller.EventController;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.error.GlobalExceptionHandler;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper.EventWebMapperImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;

@WebFluxTest(controllers = EventController.class)
@Import({EventWebMapperImpl.class, GlobalExceptionHandler.class})
class EventControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RegisterEventUseCasePort registerEventUseCase;

    @MockitoBean
    private ViewEventDetailsUseCasePort viewEventDetailsUseCase;

    @MockitoBean
    private ConsultEventsUseCasePort consultEventsUseCase;

    @MockitoBean
    private SearchEventsUseCasePort searchEventsUseCase;

    @MockitoBean
    private ChangeEventStatusUseCasePort changeEventStatusUseCase;

    @MockitoBean
    private EstimateEventAttendanceUseCasePort estimateEventAttendanceUseCase;

    @Autowired
    void configureClient(WebTestClient webTestClient) {
        this.webTestClient = webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockUser("tester"))
                .mutateWith(SecurityMockServerConfigurers.csrf());
    }

    private Event eventWith(UUID id, EventStatus status) {
        Instant startsAt = Instant.parse("2026-11-01T10:00:00Z");
        return Event.builder()
                .id(id)
                .siteId(UUID.randomUUID())
                .name("Sunset Kayak Tour")
                .category("Nautical")
                .status(status)
                .price(new BigDecimal("150000.00"))
                .maxCapacity(12)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(7200))
                .description("Guided kayak tour at sunset.")
                .build();
    }

    @Test
    void registerMapsRequestAndReturnsCreatedEvent() {
        var registered = eventWith(UUID.randomUUID(), EventStatus.DRAFT);
        given(registerEventUseCase.handle(argThat(event ->
                "Sunset Kayak Tour".equals(event.getName())
                        && "Nautical".equals(event.getCategory())
                        && event.getPrice().compareTo(new BigDecimal("150000.00")) == 0
                        && event.getMaxCapacity() == 12
                        && event.getStatus() == EventStatus.DRAFT)))
                .willReturn(Mono.just(registered));

        webTestClient.post().uri("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "siteId": "%s",
                          "name": "Sunset Kayak Tour",
                          "category": "Nautical",
                          "price": 150000.00,
                          "maxCapacity": 12,
                          "startsAt": "2026-11-01T10:00:00Z",
                          "endsAt": "2026-11-01T12:00:00Z",
                          "description": "Guided kayak tour at sunset."
                        }
                        """.formatted(registered.getSiteId()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(registered.getId().toString())
                .jsonPath("$.name").isEqualTo("Sunset Kayak Tour")
                .jsonPath("$.category").isEqualTo("Nautical")
                .jsonPath("$.status").isEqualTo("DRAFT")
                .jsonPath("$.maxCapacity").isEqualTo(12)
                .jsonPath("$.expectedAttendance").isEqualTo(0);
    }

    @Test
    void registerRejectsInvalidBody() {
        webTestClient.post().uri("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name": "Missing site and schedule",
                          "category": "Nautical",
                          "price": 1000
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void findOneReturnsEvent() {
        var id = UUID.randomUUID();
        given(viewEventDetailsUseCase.handle(id)).willReturn(Mono.just(eventWith(id, EventStatus.PUBLISHED)));

        webTestClient.get().uri("/api/v1/events/{eventId}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.status").isEqualTo("PUBLISHED");
    }

    @Test
    void findOneReturnsNotFoundWhenEventDoesNotExist() {
        var id = UUID.randomUUID();
        given(viewEventDetailsUseCase.handle(id)).willReturn(Mono.error(new EventNotFoundException(id)));

        webTestClient.get().uri("/api/v1/events/{eventId}", id)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void searchReturnsMatchingEvents() {
        given(searchEventsUseCase.handle("Kayak")).willReturn(Flux.just(
                eventWith(UUID.randomUUID(), EventStatus.DRAFT)));

        webTestClient.get().uri("/api/v1/events/search?name=Kayak")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].name").isEqualTo("Sunset Kayak Tour");
    }

    @Test
    void changeStatusMapsInvalidTransitionToConflict() {
        var id = UUID.randomUUID();
        given(changeEventStatusUseCase.handle(id, EventStatus.PUBLISHED))
                .willReturn(Mono.error(new InvalidEventStatusTransitionException(
                        "Cannot change event from CANCELLED to PUBLISHED")));

        webTestClient.patch().uri("/api/v1/events/{eventId}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"status": "PUBLISHED"}
                        """)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void changeStatusReturnsUpdatedEvent() {
        var id = UUID.randomUUID();
        given(changeEventStatusUseCase.handle(id, EventStatus.PUBLISHED)).willReturn(Mono.just(
                eventWith(id, EventStatus.PUBLISHED)));

        webTestClient.patch().uri("/api/v1/events/{eventId}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"status": "PUBLISHED"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PUBLISHED");
    }

    @Test
    void estimateAttendanceReturnsUpdatedEvent() {
        var id = UUID.randomUUID();
        var updated = eventWith(id, EventStatus.DRAFT);
        updated.estimateAttendance(200);
        given(estimateEventAttendanceUseCase.handle(id, 200)).willReturn(Mono.just(updated));

        webTestClient.patch().uri("/api/v1/events/{eventId}/attendance", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"expectedAttendance": 200}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.expectedAttendance").isEqualTo(200);
    }

    @Test
    void findAllReturnsEvents() {
        given(consultEventsUseCase.handle()).willReturn(Flux.just(
                eventWith(UUID.randomUUID(), EventStatus.DRAFT)));

        webTestClient.get().uri("/api/v1/events")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void domainValidationErrorMapsToBadRequest() {
        given(registerEventUseCase.handle(any())).willReturn(Mono.error(
                new InvalidEventNameException("Name must not exceed 150 characters.")));

        webTestClient.post().uri("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "siteId": "%s",
                          "name": "Sunset Kayak Tour",
                          "category": "Nautical",
                          "price": 150000.00,
                          "maxCapacity": 12,
                          "startsAt": "2026-11-01T10:00:00Z",
                          "endsAt": "2026-11-01T12:00:00Z"
                        }
                        """.formatted(UUID.randomUUID()))
                .exchange()
                .expectStatus().isBadRequest();
    }
}
