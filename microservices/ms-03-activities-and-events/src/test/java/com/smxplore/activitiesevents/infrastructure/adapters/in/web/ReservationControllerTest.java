package com.smxplore.activitiesevents.infrastructure.adapters.in.web;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.reservation.PaymentMethod;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.CancelReservationUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultOneReservationUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByGuideUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByItemUseCasePort;
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

import static org.mockito.BDDMockito.given;

@WebFluxTest(controllers = ReservationController.class)
@Import({ReservationWebMapperImpl.class, GlobalExceptionHandler.class})
class ReservationControllerTest {

    private static final UUID TOURIST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RESERVATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private WebTestClient webTestClient;

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
    private CancelReservationUseCasePort cancelReservationUseCase;

    @MockitoBean
    private RegisterReservationAttendanceUseCasePort registerReservationAttendanceUseCase;

    @MockitoBean
    private RegisterReservationNoShowUseCasePort registerReservationNoShowUseCase;

    @MockitoBean
    private ActivityRepositoryPort activityRepository;

    @MockitoBean
    private EventRepositoryPort eventRepository;

    @Autowired
    void configureClient(WebTestClient webTestClient) {
        this.webTestClient = webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(jwt -> jwt.subject(TOURIST_ID.toString())))
                .mutateWith(SecurityMockServerConfigurers.csrf());
    }

    @Test
    void listingOmitsTheNotesButKeepsTheTouristId() {
        given(consultReservationsByStatusUseCase.handle(ReservationStatus.CONFIRMED, 50))
                .willReturn(Flux.just(reservation()));

        webTestClient.get().uri("/api/v1/reservations?status=CONFIRMED")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo(RESERVATION_ID.toString())
                .jsonPath("$[0].touristId").isEqualTo(TOURIST_ID.toString())
                .jsonPath("$[0].notes").doesNotExist();
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

    private Reservation reservation() {
        var startsAt = Instant.now().plusSeconds(3600);
        return Reservation.builder()
                .id(RESERVATION_ID)
                .touristId(TOURIST_ID)
                .itemType(ReservationItemType.ACTIVITY)
                .itemId(UUID.randomUUID())
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
