package com.smxplore.activitiesevents.application.activities;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityCapacityException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityRequestException;
import com.smxplore.activitiesevents.domain.exceptions.activity.InvalidActivityStatusTransitionException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityUseCasesTest {

    private FakeActivityRepository activityRepo;
    private RegisterActivityUseCase registerActivity;
    private UpdateActivityUseCase updateActivity;
    private ManageAvailabilityUseCase manageAvailability;
    private ManageSlotsUseCase manageSlots;
    private CancelActivityUseCase cancelActivity;
    private ChangeActivityStatusUseCase changeActivityStatus;
    private ConsultActivitiesUseCase consultActivities;
    private FindActivitiesByCategoryUseCase findActivitiesByCategory;
    private FindActivitiesBySiteUseCase findActivitiesBySite;
    private ViewActivityDetailsUseCase viewActivityDetails;

    @BeforeEach
    void setUp() {
        activityRepo = new FakeActivityRepository();
        registerActivity = new RegisterActivityUseCase(activityRepo);
        updateActivity = new UpdateActivityUseCase(activityRepo);
        manageAvailability = new ManageAvailabilityUseCase(activityRepo);
        manageSlots = new ManageSlotsUseCase(activityRepo);
        cancelActivity = new CancelActivityUseCase(activityRepo);
        changeActivityStatus = new ChangeActivityStatusUseCase(activityRepo);
        consultActivities = new ConsultActivitiesUseCase(activityRepo);
        findActivitiesByCategory = new FindActivitiesByCategoryUseCase(activityRepo);
        findActivitiesBySite = new FindActivitiesBySiteUseCase(activityRepo);
        viewActivityDetails = new ViewActivityDetailsUseCase(activityRepo);
    }

    private Activity validActivity(UUID id) {
        Instant startsAt = Instant.now().plusSeconds(3600);
        return Activity.builder()
                .id(id)
                .siteId(UUID.randomUUID())
                .guideId(UUID.randomUUID())
                .name("Snorkel at Playa Cristal")
                .category("Water Sports")
                .price(new BigDecimal("120000.00"))
                .maxCapacity(10)
                .durationMinutes(120)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(7200))
                .description("Guided snorkeling activity.")
                .build();
    }

    @Test
    void shouldRegisterValidActivity() {
        UUID id = UUID.randomUUID();

        Activity saved = Objects.requireNonNull(registerActivity.handle(validActivity(id)).block());

        assertEquals(id, saved.getId());
        assertTrue(Objects.requireNonNull(activityRepo.existsById(id).block()));
    }

    @Test
    void shouldFailRegisterWhenActivityIsNull() {
        assertThrows(InvalidActivityRequestException.class, () -> registerActivity.handle(null).block());
    }

    @Test
    void shouldPropagateValidationErrorOnRegister() {
        Activity invalid = validActivity(UUID.randomUUID()).toBuilder().siteId(null).build();

        assertThrows(InvalidActivityRequestException.class, () -> registerActivity.handle(invalid).block());
    }

    @Test
    void shouldRegisterActivityWithoutGuide() {
        UUID id = UUID.randomUUID();
        Activity withoutGuide = validActivity(id).toBuilder().guideId(null).build();

        Activity saved = Objects.requireNonNull(registerActivity.handle(withoutGuide).block());

        assertNull(saved.getGuideId());
    }

    @Test
    void shouldUpdateActivityDetails() {
        UUID id = UUID.randomUUID();
        activityRepo.save(validActivity(id)).block();

        Activity updated = Objects.requireNonNull(updateActivity.handle(id, "Sunset Kayak", "Nautical",
                "New description", new BigDecimal("99999.00")).block());

        assertEquals("Sunset Kayak", updated.getName());
        assertEquals("Nautical", updated.getCategory());
        assertEquals("New description", updated.getDescription());
        assertEquals(new BigDecimal("99999.00"), updated.getPrice());
    }

    @Test
    void shouldFailUpdateWhenActivityDoesNotExist() {
        assertThrows(ActivityNotFoundException.class,
                () -> updateActivity.handle(UUID.randomUUID(), "Name", "Category", "Desc",
                        new BigDecimal("1000.00")).block());
    }

    @Test
    void shouldUpdateAvailability() {
        UUID id = UUID.randomUUID();
        activityRepo.save(validActivity(id)).block();
        Instant newStart = Instant.now().plusSeconds(7200);
        Instant newEnd = newStart.plusSeconds(3600);

        Activity updated = Objects.requireNonNull(manageAvailability.handle(id, newStart, newEnd).block());

        assertEquals(newStart, updated.getStartsAt());
        assertEquals(newEnd, updated.getEndsAt());
    }

    @Test
    void shouldFailAvailabilityWhenActivityDoesNotExist() {
        Instant startsAt = Instant.now().plusSeconds(3600);

        assertThrows(ActivityNotFoundException.class,
                () -> manageAvailability.handle(UUID.randomUUID(), startsAt, startsAt.plusSeconds(3600)).block());
    }

    @Test
    void shouldManageSlots() {
        UUID id = UUID.randomUUID();
        activityRepo.save(validActivity(id)).block();

        Activity updated = Objects.requireNonNull(manageSlots.handle(id, 25).block());

        assertEquals(25, updated.getMaxCapacity());
    }

    @Test
    void shouldFailSlotsWhenActivityDoesNotExist() {
        assertThrows(ActivityNotFoundException.class,
                () -> manageSlots.handle(UUID.randomUUID(), 10).block());
    }

    @Test
    void shouldFailSlotsWithInvalidCapacity() {
        UUID id = UUID.randomUUID();
        activityRepo.save(validActivity(id)).block();

        assertThrows(InvalidActivityCapacityException.class, () -> manageSlots.handle(id, 0).block());
    }

    @Test
    void shouldCancelActivity() {
        UUID id = UUID.randomUUID();
        activityRepo.save(validActivity(id).toBuilder().status(ActivityStatus.PUBLISHED).build()).block();

        Activity updated = Objects.requireNonNull(cancelActivity.handle(id).block());

        assertEquals(ActivityStatus.CANCELLED, updated.getStatus());
    }

    @Test
    void shouldFailCancelWhenActivityDoesNotExist() {
        assertThrows(ActivityNotFoundException.class,
                () -> cancelActivity.handle(UUID.randomUUID()).block());
    }

    @Test
    void shouldFailCancelWhenActivityIsAlreadyCancelled() {
        UUID id = UUID.randomUUID();
        activityRepo.save(validActivity(id).toBuilder().status(ActivityStatus.CANCELLED).build()).block();

        assertThrows(InvalidActivityStatusTransitionException.class, () -> cancelActivity.handle(id).block());
    }

    @Test
    void shouldChangeStatusOfExistingActivity() {
        UUID id = UUID.randomUUID();
        activityRepo.save(validActivity(id)).block();

        Activity updated = Objects.requireNonNull(changeActivityStatus.handle(id, ActivityStatus.PUBLISHED).block());

        assertEquals(ActivityStatus.PUBLISHED, updated.getStatus());
    }

    @Test
    void shouldPausePublishedActivity() {
        UUID id = UUID.randomUUID();
        activityRepo.save(validActivity(id).toBuilder().status(ActivityStatus.PUBLISHED).build()).block();

        Activity updated = Objects.requireNonNull(changeActivityStatus.handle(id, ActivityStatus.PAUSED).block());

        assertEquals(ActivityStatus.PAUSED, updated.getStatus());
    }

    @Test
    void shouldFailChangingStatusWhenActivityDoesNotExist() {
        assertThrows(ActivityNotFoundException.class,
                () -> changeActivityStatus.handle(UUID.randomUUID(), ActivityStatus.PUBLISHED).block());
    }

    @Test
    void shouldFailChangingStatusWithInvalidTransition() {
        UUID id = UUID.randomUUID();
        activityRepo.save(validActivity(id).toBuilder().status(ActivityStatus.CANCELLED).build()).block();

        assertThrows(InvalidActivityStatusTransitionException.class,
                () -> changeActivityStatus.handle(id, ActivityStatus.PUBLISHED).block());
    }

    @Test
    void shouldConsultAllActivities() {
        activityRepo.save(validActivity(UUID.randomUUID())).block();
        activityRepo.save(validActivity(UUID.randomUUID())).block();

        List<Activity> activities = Objects.requireNonNull(consultActivities.handle().collectList().block());

        assertEquals(2, activities.size());
    }

    @Test
    void shouldFindActivitiesByCategory() {
        activityRepo.save(validActivity(UUID.randomUUID())).block();
        activityRepo.save(validActivity(UUID.randomUUID()).toBuilder().category("Nautical").build()).block();

        List<Activity> matches = Objects.requireNonNull(
                findActivitiesByCategory.handle("Water Sports").collectList().block());

        assertEquals(1, matches.size());
        assertEquals("Water Sports", matches.getFirst().getCategory());
    }

    @Test
    void shouldFindActivitiesBySite() {
        UUID siteId = UUID.randomUUID();
        activityRepo.save(validActivity(UUID.randomUUID()).toBuilder().siteId(siteId).build()).block();
        activityRepo.save(validActivity(UUID.randomUUID()).toBuilder().siteId(siteId).build()).block();
        activityRepo.save(validActivity(UUID.randomUUID())).block();

        List<Activity> matches = Objects.requireNonNull(findActivitiesBySite.handle(siteId).collectList().block());

        assertEquals(2, matches.size());
    }

    @Test
    void shouldViewActivityDetails() {
        UUID id = UUID.randomUUID();
        activityRepo.save(validActivity(id)).block();

        Activity found = Objects.requireNonNull(viewActivityDetails.handle(id).block());

        assertEquals(id, found.getId());
    }

    @Test
    void shouldFailViewDetailsWhenActivityDoesNotExist() {
        assertThrows(ActivityNotFoundException.class,
                () -> viewActivityDetails.handle(UUID.randomUUID()).block());
    }
}
