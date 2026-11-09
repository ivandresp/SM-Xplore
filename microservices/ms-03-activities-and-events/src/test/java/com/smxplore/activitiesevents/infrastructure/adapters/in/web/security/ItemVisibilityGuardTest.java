package com.smxplore.activitiesevents.infrastructure.adapters.in.web.security;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ItemVisibilityGuardTest {

    private static final UUID GUIDE_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();

    @Mock
    private ActivityRepositoryPort activityRepository;

    @Mock
    private EventRepositoryPort eventRepository;

    private ItemVisibilityGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ItemVisibilityGuard(activityRepository, eventRepository);
        ReflectionTestUtils.setField(guard, "roleClaim", "role");
    }

    @Test
    void publishedActivitiesAreVisibleToAnonymousCallers() {
        var activity = activity(ActivityStatus.PUBLISHED, GUIDE_ID);
        given(activityRepository.findById(activity.getId())).willReturn(Mono.just(activity));

        StepVerifier.create(guard.requireVisible(activity.getId(), ReservationItemType.ACTIVITY, null))
                .verifyComplete();
    }

    @Test
    void draftActivitiesAreHiddenFromAnonymousCallers() {
        var activity = activity(ActivityStatus.DRAFT, GUIDE_ID);
        given(activityRepository.findById(activity.getId())).willReturn(Mono.just(activity));

        StepVerifier.create(guard.requireVisible(activity.getId(), ReservationItemType.ACTIVITY, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                })
                .verify();
    }

    @Test
    void anAdminCanSeeAnyDraft() {
        var activity = activity(ActivityStatus.DRAFT, GUIDE_ID);
        given(activityRepository.findById(activity.getId())).willReturn(Mono.just(activity));

        StepVerifier.create(guard.requireVisible(activity.getId(), ReservationItemType.ACTIVITY, jwt("ADMIN")))
                .verifyComplete();
    }

    @Test
    void theGuideOfRecordCanSeeItsOwnDraft() {
        var activity = activity(ActivityStatus.DRAFT, GUIDE_ID);
        given(activityRepository.findById(activity.getId())).willReturn(Mono.just(activity));

        StepVerifier.create(guard.requireVisible(activity.getId(), ReservationItemType.ACTIVITY,
                        jwt("GUIDE", GUIDE_ID)))
                .verifyComplete();
    }

    @Test
    void anotherGuideCannotSeeTheDraft() {
        var activity = activity(ActivityStatus.DRAFT, UUID.randomUUID());
        given(activityRepository.findById(activity.getId())).willReturn(Mono.just(activity));

        StepVerifier.create(guard.requireVisible(activity.getId(), ReservationItemType.ACTIVITY,
                        jwt("GUIDE", GUIDE_ID)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                })
                .verify();
    }

    @Test
    void theManagerOfRecordCanSeeItsDraftEvent() {
        var event = event(EventStatus.DRAFT, MANAGER_ID);
        given(eventRepository.findById(event.getId())).willReturn(Mono.just(event));

        StepVerifier.create(guard.requireVisible(event.getId(), ReservationItemType.EVENT,
                        jwt("DESTINATION_MANAGER", MANAGER_ID)))
                .verifyComplete();
    }

    @Test
    void publishedEventsAreVisibleToAnonymousCallers() {
        var event = event(EventStatus.PUBLISHED, MANAGER_ID);
        given(eventRepository.findById(event.getId())).willReturn(Mono.just(event));

        StepVerifier.create(guard.requireVisible(event.getId(), ReservationItemType.EVENT, null))
                .verifyComplete();
    }

    private Jwt jwt(String role) {
        return jwt(role, UUID.randomUUID());
    }

    private Jwt jwt(String role, UUID subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject.toString())
                .claim("role", role)
                .build();
    }

    private Activity activity(ActivityStatus status, UUID guideId) {
        Instant startsAt = Instant.now().plusSeconds(3_600);
        return Activity.builder()
                .id(UUID.randomUUID())
                .siteId(UUID.randomUUID())
                .guideId(guideId)
                .name("Snorkel")
                .category("Water")
                .status(status)
                .price(new BigDecimal("80000.00"))
                .maxCapacity(10)
                .durationMinutes(60)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(3_600))
                .build();
    }

    private Event event(EventStatus status, UUID managerId) {
        Instant startsAt = Instant.now().plusSeconds(3_600);
        return Event.builder()
                .id(UUID.randomUUID())
                .siteId(UUID.randomUUID())
                .managerId(managerId)
                .name("Kayak")
                .category("Nautical")
                .status(status)
                .price(new BigDecimal("150000.00"))
                .maxCapacity(20)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(3_600))
                .build();
    }
}
