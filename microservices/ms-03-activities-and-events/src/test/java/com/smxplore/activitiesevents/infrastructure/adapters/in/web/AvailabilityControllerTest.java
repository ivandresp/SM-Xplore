package com.smxplore.activitiesevents.infrastructure.adapters.in.web;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.availability.Availability;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.availability.ConsultAvailabilityUseCasePort;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller.AvailabilityController;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.error.GlobalExceptionHandler;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.security.ItemVisibilityGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@WebFluxTest(controllers = AvailabilityController.class)
@Import({GlobalExceptionHandler.class, ItemVisibilityGuard.class})
class AvailabilityControllerTest {

    private static final UUID GUIDE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ConsultAvailabilityUseCasePort consultAvailabilityUseCase;

    @MockitoBean
    private ActivityRepositoryPort activityRepository;

    @MockitoBean
    private EventRepositoryPort eventRepository;

    private WebTestClient baseClient;

    @Autowired
    void configureClient(WebTestClient webTestClient) {
        this.baseClient = webTestClient;
        this.webTestClient = webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(jwt -> jwt.subject(GUIDE_ID.toString()).claim("role", "GUIDE")))
                .mutateWith(SecurityMockServerConfigurers.csrf());
    }

    private WebTestClient touristClient() {
        return baseClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("role", "TOURIST")))
                .mutateWith(SecurityMockServerConfigurers.csrf());
    }

    private WebTestClient adminClient() {
        return baseClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("role", "ADMIN")))
                .mutateWith(SecurityMockServerConfigurers.csrf());
    }

    private WebTestClient guideClient(UUID guideId) {
        return baseClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(jwt -> jwt.subject(guideId.toString()).claim("role", "GUIDE")))
                .mutateWith(SecurityMockServerConfigurers.csrf());
    }

    private Activity activity(UUID id, ActivityStatus status, UUID guideId) {
        Instant startsAt = Instant.now().plusSeconds(3600);
        return Activity.builder()
                .id(id)
                .siteId(UUID.randomUUID())
                .guideId(guideId)
                .name("Kayak tour")
                .category("Adventure")
                .status(status)
                .price(new BigDecimal("120000.00"))
                .maxCapacity(10)
                .durationMinutes(120)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(7200))
                .build();
    }

    private Availability availability(UUID itemId) {
        return new Availability(itemId, ReservationItemType.ACTIVITY, 10, 4, 6);
    }

    @Test
    void returnsTheAvailabilityOfAPublishedActivityToAnyVisitor() {
        var activity = activity(UUID.randomUUID(), ActivityStatus.PUBLISHED, UUID.randomUUID());
        given(activityRepository.findById(activity.getId())).willReturn(Mono.just(activity));
        given(consultAvailabilityUseCase.handle(activity.getId(), ReservationItemType.ACTIVITY))
                .willReturn(Mono.just(availability(activity.getId())));

        touristClient().get()
                .uri("/api/v1/activities/{id}/availability", activity.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.itemId").isEqualTo(activity.getId().toString())
                .jsonPath("$.maxCapacity").isEqualTo(10)
                .jsonPath("$.reserved").isEqualTo(4)
                .jsonPath("$.available").isEqualTo(6);
    }

    @Test
    void hidesTheAvailabilityOfADraftActivityFromVisitors() {
        var activity = activity(UUID.randomUUID(), ActivityStatus.DRAFT, UUID.randomUUID());
        given(activityRepository.findById(activity.getId())).willReturn(Mono.just(activity));

        touristClient().get()
                .uri("/api/v1/activities/{id}/availability", activity.getId())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void letsAnAdminSeeTheAvailabilityOfADraftActivity() {
        var activity = activity(UUID.randomUUID(), ActivityStatus.DRAFT, UUID.randomUUID());
        given(activityRepository.findById(activity.getId())).willReturn(Mono.just(activity));
        given(consultAvailabilityUseCase.handle(any(), any()))
                .willReturn(Mono.just(availability(activity.getId())));

        adminClient().get()
                .uri("/api/v1/activities/{id}/availability", activity.getId())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void letsTheGuideOfRecordSeeTheAvailabilityOfItsDraftActivity() {
        var activity = activity(UUID.randomUUID(), ActivityStatus.DRAFT, GUIDE_ID);
        given(activityRepository.findById(activity.getId())).willReturn(Mono.just(activity));
        given(consultAvailabilityUseCase.handle(any(), any()))
                .willReturn(Mono.just(availability(activity.getId())));

        guideClient(GUIDE_ID).get()
                .uri("/api/v1/activities/{id}/availability", activity.getId())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void hidesTheAvailabilityOfAnotherGuidesDraftActivity() {
        var activity = activity(UUID.randomUUID(), ActivityStatus.DRAFT, UUID.randomUUID());
        given(activityRepository.findById(activity.getId())).willReturn(Mono.just(activity));

        guideClient(GUIDE_ID).get()
                .uri("/api/v1/activities/{id}/availability", activity.getId())
                .exchange()
                .expectStatus().isNotFound();
    }
}
