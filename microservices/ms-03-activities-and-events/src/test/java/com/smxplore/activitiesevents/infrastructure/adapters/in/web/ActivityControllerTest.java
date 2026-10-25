package com.smxplore.activitiesevents.infrastructure.adapters.in.web;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityStatusTransitionException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.CancelActivityUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ChangeActivityStatusUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ConsultActivitiesUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.FindActivitiesByCategoryUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.FindActivitiesBySiteUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ManageAvailabilityUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ManageSlotsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.RegisterActivityUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.UpdateActivityUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ViewActivityDetailsUseCasePort;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller.ActivityController;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.error.GlobalExceptionHandler;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper.ActivityWebMapperImpl;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.security.ActivityAccessGuard;
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;

@WebFluxTest(controllers = ActivityController.class)
@Import({ActivityWebMapperImpl.class, GlobalExceptionHandler.class, ActivityAccessGuard.class})
class ActivityControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RegisterActivityUseCasePort registerActivityUseCase;

    @MockitoBean
    private ViewActivityDetailsUseCasePort viewActivityDetailsUseCase;

    @MockitoBean
    private ConsultActivitiesUseCasePort consultActivitiesUseCase;

    @MockitoBean
    private FindActivitiesByCategoryUseCasePort findActivitiesByCategoryUseCase;

    @MockitoBean
    private FindActivitiesBySiteUseCasePort findActivitiesBySiteUseCase;

    @MockitoBean
    private UpdateActivityUseCasePort updateActivityUseCase;

    @MockitoBean
    private ManageAvailabilityUseCasePort manageAvailabilityUseCase;

    @MockitoBean
    private ManageSlotsUseCasePort manageSlotsUseCase;

    @MockitoBean
    private CancelActivityUseCasePort cancelActivityUseCase;

    @MockitoBean
    private ChangeActivityStatusUseCasePort changeActivityStatusUseCase;

    @Autowired
    void configureClient(WebTestClient webTestClient) {
        this.baseClient = webTestClient;
        this.webTestClient = webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(jwt -> jwt.subject(GUIDE_ID.toString()).claim("role", "ADMIN")))
                .mutateWith(SecurityMockServerConfigurers.csrf());
    }

    private static final UUID GUIDE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private WebTestClient baseClient;

    private WebTestClient guideClient() {
        return baseClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(jwt -> jwt.subject(GUIDE_ID.toString()).claim("role", "GUIDE")))
                .mutateWith(SecurityMockServerConfigurers.csrf());
    }

    private Activity activityWith(UUID id, ActivityStatus status) {
        Instant startsAt = Instant.parse("2026-11-01T10:00:00Z");
        return Activity.builder()
                .id(id)
                .siteId(UUID.randomUUID())
                .guideId(UUID.randomUUID())
                .name("City Walking Tour")
                .category("Tour")
                .status(status)
                .price(new BigDecimal("80000.00"))
                .maxCapacity(20)
                .durationMinutes(90)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(5400))
                .description("Historic downtown walk.")
                .build();
    }

    @Test
    void registerMapsRequestAndReturnsCreatedActivity() {
        var registered = activityWith(UUID.randomUUID(), ActivityStatus.DRAFT);
        given(registerActivityUseCase.handle(argThat(activity ->
                "City Walking Tour".equals(activity.getName())
                        && "Tour".equals(activity.getCategory())
                        && activity.getDurationMinutes() == 90
                        && activity.getMaxCapacity() == 20
                        && activity.getStatus() == ActivityStatus.DRAFT)))
                .willReturn(Mono.just(registered));

        webTestClient.post().uri("/api/v1/activities")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "siteId": "%s",
                          "guideId": "%s",
                          "name": "City Walking Tour",
                          "category": "Tour",
                          "price": 80000.00,
                          "maxCapacity": 20,
                          "durationMinutes": 90,
                          "startsAt": "2026-11-01T10:00:00Z",
                          "endsAt": "2026-11-01T11:30:00Z",
                          "description": "Historic downtown walk."
                        }
                        """.formatted(registered.getSiteId(), registered.getGuideId()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(registered.getId().toString())
                .jsonPath("$.name").isEqualTo("City Walking Tour")
                .jsonPath("$.category").isEqualTo("Tour")
                .jsonPath("$.status").isEqualTo("DRAFT")
                .jsonPath("$.durationMinutes").isEqualTo(90);
    }

    @Test
    void registeredActivityMayHaveNoGuide() {
        var withoutGuide = activityWith(UUID.randomUUID(), ActivityStatus.DRAFT).toBuilder()
                .guideId(null)
                .build();
        given(registerActivityUseCase.handle(argThat(activity -> activity.getGuideId() == null)))
                .willReturn(Mono.just(withoutGuide));

        webTestClient.post().uri("/api/v1/activities")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "siteId": "%s",
                          "name": "Free Museum Day",
                          "category": "Culture",
                          "price": 0,
                          "maxCapacity": 50,
                          "durationMinutes": 120,
                          "startsAt": "2026-11-01T10:00:00Z",
                          "endsAt": "2026-11-01T12:00:00Z",
                          "description": "Open doors day."
                        }
                        """.formatted(withoutGuide.getSiteId()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.guideId").doesNotExist();
    }

    @Test
    void findByCategoryReturnsMatchingActivities() {
        given(findActivitiesByCategoryUseCase.handle("Tour")).willReturn(Flux.just(
                activityWith(UUID.randomUUID(), ActivityStatus.DRAFT)));

        webTestClient.get().uri("/api/v1/activities/search?category=Tour")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].category").isEqualTo("Tour");
    }

    @Test
    void findBySiteReturnsActivities() {
        var siteId = UUID.randomUUID();
        given(findActivitiesBySiteUseCase.handle(siteId)).willReturn(Flux.just(
                activityWith(UUID.randomUUID(), ActivityStatus.DRAFT)));

        webTestClient.get().uri("/api/v1/activities/site/{siteId}", siteId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void updateChangesActivityDetails() {
        var id = UUID.randomUUID();
        var updated = activityWith(id, ActivityStatus.DRAFT).toBuilder()
                .name("City Walking Tour Deluxe")
                .price(new BigDecimal("95000.00"))
                .description("Updated description.")
                .build();
        given(updateActivityUseCase.handle(id, "City Walking Tour Deluxe", "Tour",
                "Updated description.", new BigDecimal("95000.00")))
                .willReturn(Mono.just(updated));

        webTestClient.patch().uri("/api/v1/activities/{activityId}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name": "City Walking Tour Deluxe",
                          "category": "Tour",
                          "description": "Updated description.",
                          "price": 95000.00
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("City Walking Tour Deluxe");
    }

    @Test
    void manageAvailabilityUpdatesTheWindow() {
        var id = UUID.randomUUID();
        var startsAt = Instant.parse("2026-11-02T09:00:00Z");
        var endsAt = Instant.parse("2026-11-02T11:00:00Z");
        var updated = activityWith(id, ActivityStatus.DRAFT).toBuilder()
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build();
        given(manageAvailabilityUseCase.handle(id, startsAt, endsAt)).willReturn(Mono.just(updated));

        webTestClient.patch().uri("/api/v1/activities/{activityId}/availability", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "startsAt": "2026-11-02T09:00:00Z",
                          "endsAt": "2026-11-02T11:00:00Z"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.startsAt").isEqualTo("2026-11-02T09:00:00Z");
    }

    @Test
    void manageSlotsRejectsNonPositiveCapacity() {
        var id = UUID.randomUUID();

        webTestClient.patch().uri("/api/v1/activities/{activityId}/slots", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"maxCapacity": 0}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void cancelReturnsCancelledActivity() {
        var id = UUID.randomUUID();
        given(cancelActivityUseCase.handle(id)).willReturn(Mono.just(
                activityWith(id, ActivityStatus.CANCELLED)));

        webTestClient.post().uri("/api/v1/activities/{activityId}/cancel", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED");
    }

    @Test
    void cancelReturnsNotFoundWhenActivityDoesNotExist() {
        var id = UUID.randomUUID();
        given(cancelActivityUseCase.handle(id)).willReturn(Mono.error(new ActivityNotFoundException(id)));

        webTestClient.post().uri("/api/v1/activities/{activityId}/cancel", id)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void findOneReturnsActivity() {
        var id = UUID.randomUUID();
        given(viewActivityDetailsUseCase.handle(id)).willReturn(Mono.just(
                activityWith(id, ActivityStatus.DRAFT)));

        webTestClient.get().uri("/api/v1/activities/{activityId}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString());
    }

    @Test
    void findAllReturnsActivities() {
        given(consultActivitiesUseCase.handle()).willReturn(Flux.just(
                activityWith(UUID.randomUUID(), ActivityStatus.DRAFT)));

        webTestClient.get().uri("/api/v1/activities")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void changeStatusReturnsUpdatedActivity() {
        var id = UUID.randomUUID();
        given(changeActivityStatusUseCase.handle(id, ActivityStatus.PUBLISHED)).willReturn(Mono.just(
                activityWith(id, ActivityStatus.PUBLISHED)));

        webTestClient.patch().uri("/api/v1/activities/{activityId}/status", id)
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
    void changeStatusMapsInvalidTransitionToConflict() {
        var id = UUID.randomUUID();
        given(changeActivityStatusUseCase.handle(id, ActivityStatus.PUBLISHED)).willReturn(Mono.error(
                new InvalidActivityStatusTransitionException(
                        "Cannot change activity from CANCELLED to PUBLISHED")));

        webTestClient.patch().uri("/api/v1/activities/{activityId}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"status": "PUBLISHED"}
                        """)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void guideRegistrationIgnoresGuideIdFromBody() {
        var siteId = UUID.randomUUID();
        var otherGuide = UUID.randomUUID();
        var registered = activityWith(UUID.randomUUID(), ActivityStatus.DRAFT).toBuilder()
                .guideId(GUIDE_ID)
                .build();
        given(registerActivityUseCase.handle(argThat(activity -> GUIDE_ID.equals(activity.getGuideId()))))
                .willReturn(Mono.just(registered));

        guideClient().post().uri("/api/v1/activities")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "siteId": "%s",
                          "guideId": "%s",
                          "name": "City Walking Tour",
                          "category": "Tour",
                          "price": 80000.00,
                          "maxCapacity": 20,
                          "durationMinutes": 90,
                          "startsAt": "2026-11-01T10:00:00Z",
                          "endsAt": "2026-11-01T11:30:00Z",
                          "description": "Historic downtown walk."
                        }
                        """.formatted(siteId, otherGuide))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.guideId").isEqualTo(GUIDE_ID.toString());
    }

    @Test
    void guideCannotManageAnotherGuidesActivity() {
        var id = UUID.randomUUID();
        var otherGuide = UUID.randomUUID();
        given(viewActivityDetailsUseCase.handle(id)).willReturn(Mono.just(
                activityWith(id, ActivityStatus.PUBLISHED).toBuilder().guideId(otherGuide).build()));

        guideClient().patch().uri("/api/v1/activities/{activityId}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name": "Hijacked",
                          "category": "Tour",
                          "price": 1,
                          "description": "Should not be applied."
                        }
                        """)
                .exchange()
                .expectStatus().isForbidden();
    }
}
