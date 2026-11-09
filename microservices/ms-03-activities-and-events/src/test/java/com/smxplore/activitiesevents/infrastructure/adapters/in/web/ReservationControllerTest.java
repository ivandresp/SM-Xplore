package com.smxplore.activitiesevents.infrastructure.adapters.in.web;

import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.reservation.PaymentMethod;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.application.reservations.ConsultManagerReservationsUseCase;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.CancelReservationUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultOneReservationUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByGuideUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByItemUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByProviderUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByStatusUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByTouristUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.CreateReservationUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.RegisterReservationAttendanceUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.RegisterReservationNoShowUseCasePort;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller.ReservationController;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.error.GlobalExceptionHandler;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper.ReservationWebMapperImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@WebFluxTest(controllers = ReservationController.class)
@Import({ReservationWebMapperImpl.class, GlobalExceptionHandler.class})
class ReservationControllerTest {

    private static final UUID TOURIST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RESERVATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PROVIDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private WebTestClient webTestClient;

    private WebTestClient baseClient;

    @MockitoBean
    private CreateReservationUseCasePort createReservationUseCase;

    @MockitoBean
    private ConsultOneReservationUseCasePort consultOneReservationUseCase;

    @MockitoBean
    private ConsultReservationsByStatusUseCasePort consultReservationsByStatusUseCase;

    @MockitoBean
    private ConsultReservationsByTouristUseCasePort consultReservationsByTouristUseCase;

    @MockitoBean
    private ConsultReservationsByGuideUseCasePort consultReservationsByGuideUseCase;

    @MockitoBean
    private ConsultReservationsByItemUseCasePort consultReservationsByItemUseCase;

    @MockitoBean
    private ConsultReservationsByProviderUseCasePort consultReservationsByProviderUseCase;

    @MockitoBean
    private CancelReservationUseCasePort cancelReservationUseCase;

    @MockitoBean
    private RegisterReservationAttendanceUseCasePort registerReservationAttendanceUseCase;

    @MockitoBean
    private RegisterReservationNoShowUseCasePort registerReservationNoShowUseCase;

    @MockitoBean
    private ActivityRepositoryPort activityRepository;

    @MockitoBean
    private EventRepositoryPort eventRepository;

    @MockitoBean
    private SiteCatalogPort siteCatalog;

    @MockitoBean
    private ConsultManagerReservationsUseCase consultManagerReservationsUseCase;

    @Autowired
    void configureClient(WebTestClient webTestClient) {
        this.baseClient = webTestClient;
        this.webTestClient = webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(jwt -> jwt.subject(TOURIST_ID.toString()).claim("role", "ADMIN")))
                .mutateWith(SecurityMockServerConfigurers.csrf());
    }

    private WebTestClient clientFor(UUID subject, String role) {
        return baseClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(jwt -> jwt.subject(subject.toString()).claim("role", role)))
                .mutateWith(SecurityMockServerConfigurers.csrf());
    }

    @Test
    void listingOmitsTheNotesButKeepsTheTouristId() {
        given(consultReservationsByStatusUseCase.handle(ReservationStatus.CONFIRMED, 0, 20))
                .willReturn(Mono.just(new PageResult<>(java.util.List.of(reservation()), 0, 20, 1L)));

        webTestClient.get().uri("/api/v1/reservations?status=CONFIRMED")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].id").isEqualTo(RESERVATION_ID.toString())
                .jsonPath("$.content[0].touristId").isEqualTo(TOURIST_ID.toString())
                .jsonPath("$.content[0].notes").doesNotExist();
    }

    @Test
    void detailStillReturnsTheNotesToTheOwner() {
        given(consultOneReservationUseCase.handle(RESERVATION_ID)).willReturn(Mono.just(reservation()));

        webTestClient.get().uri("/api/v1/reservations/{id}", RESERVATION_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.notes").isEqualTo("Llegar con silla de ruedas");
    }

    @Test
    void touristListingOmitsTheNotes() {
        given(consultReservationsByTouristUseCase.handle(TOURIST_ID, 50))
                .willReturn(Flux.just(reservation()));

        webTestClient.get().uri("/api/v1/reservations/tourists/{id}", TOURIST_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].notes").doesNotExist();
    }

    @Test
    void guideListingOmitsTheNotes() {
        given(consultReservationsByGuideUseCase.handle(TOURIST_ID, null, 50))
                .willReturn(Flux.just(reservation()));

        webTestClient.get().uri("/api/v1/reservations/guides/{id}", TOURIST_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].notes").doesNotExist();
    }

    @Test
    void providerListingOmitsTheNotes() {
        given(consultReservationsByProviderUseCase.handle(PROVIDER_ID, null, 50))
                .willReturn(Flux.just(reservation()));

        clientFor(PROVIDER_ID, "SERVICE_PROVIDER")
                .get().uri("/api/v1/reservations/providers/{id}", PROVIDER_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].providerId").isEqualTo(PROVIDER_ID.toString())
                .jsonPath("$[0].notes").doesNotExist();
    }

    @Test
    void anotherProviderCannotListSomeoneElsesReservations() {
        clientFor(PROVIDER_ID, "SERVICE_PROVIDER")
                .get().uri("/api/v1/reservations/providers/{id}", UUID.randomUUID())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void adminCanListTheReservationsOfAnyProvider() {
        given(consultReservationsByProviderUseCase.handle(PROVIDER_ID, ReservationStatus.CONFIRMED, 10))
                .willReturn(Flux.just(reservation()));

        clientFor(UUID.randomUUID(), "ADMIN")
                .get().uri(uriBuilder -> uriBuilder.path("/api/v1/reservations/providers/{id}")
                        .queryParam("status", "CONFIRMED")
                        .queryParam("limit", 10)
                        .build(PROVIDER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(RESERVATION_ID.toString());
    }

    @Test
    void itemListingOmitsTheNotes() {
        var itemId = UUID.randomUUID();
        var startsAt = Instant.now().plusSeconds(3600);
        var activity = Activity.builder()
                .id(itemId)
                .siteId(UUID.randomUUID())
                .guideId(TOURIST_ID)
                .name("Snorkel")
                .category("Nautica")
                .status(ActivityStatus.PUBLISHED)
                .price(new BigDecimal("50000.00"))
                .maxCapacity(10)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(3600))
                .build();
        given(activityRepository.findById(itemId)).willReturn(Mono.just(activity));
        given(consultReservationsByItemUseCase.handle(itemId, ReservationItemType.ACTIVITY, 50))
                .willReturn(Flux.just(reservation()));

        webTestClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/reservations/by-item")
                        .queryParam("itemType", "ACTIVITY")
                        .queryParam("itemId", itemId)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].notes").doesNotExist();
    }

    @Test
    void eventItemListingUsesTheCurrentSiteManager() {
        var itemId = UUID.randomUUID();
        var managerId = UUID.randomUUID();
        var siteId = UUID.randomUUID();
        var startsAt = Instant.now().plusSeconds(3600);
        var event = Event.builder()
                .id(itemId)
                .siteId(siteId)
                .managerId(managerId)
                .name("Kayak")
                .category("Nautica")
                .status(EventStatus.DRAFT)
                .price(new BigDecimal("10000.00"))
                .maxCapacity(5)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(3600))
                .build();
        given(eventRepository.findById(itemId)).willReturn(Mono.just(event));
        given(siteCatalog.findById(eq(siteId), anyString())).willReturn(Mono.just(
                new SiteCatalogPort.SiteSnapshot(siteId, UUID.randomUUID(), "Bahia")));
        clientFor(managerId, "DESTINATION_MANAGER").get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/reservations/by-item")
                        .queryParam("itemType", "EVENT")
                        .queryParam("itemId", itemId)
                        .build())
                .exchange()
                .expectStatus().isForbidden();
    }

    private Reservation reservation() {
        var startsAt = Instant.now().plusSeconds(3600);
        return Reservation.builder()
                .id(RESERVATION_ID)
                .touristId(TOURIST_ID)
                .itemType(ReservationItemType.ACTIVITY)
                .itemId(UUID.randomUUID())
                .providerId(PROVIDER_ID)
                .itemStartsAt(startsAt)
                .unitPrice(new BigDecimal("50000.00"))
                .numberOfPeople(2)
                .total(new BigDecimal("100000.00"))
                .paymentMethod(PaymentMethod.CASH)
                .status(ReservationStatus.CONFIRMED)
                .bookedFor(startsAt)
                .notes("Llegar con silla de ruedas")
                .code("R-1")
                .build();
    }
}
