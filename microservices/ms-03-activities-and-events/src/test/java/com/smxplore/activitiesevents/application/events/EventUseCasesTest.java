package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventRequestException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventStatusTransitionException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidExpectedAttendanceException;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventUseCasesTest {

    private FakeEventRepository eventRepo;
    private RegisterEventUseCase registerEvent;
    private ChangeEventStatusUseCase changeEventStatus;
    private EstimateEventAttendanceUseCase estimateEventAttendance;
    private ConsultEventsUseCase consultEvents;
    private SearchEventsUseCase searchEvents;
    private ViewEventDetailsUseCase viewEventDetails;

    @BeforeEach
    void setUp() {
        eventRepo = new FakeEventRepository();
        var publisher = new com.smxplore.activitiesevents.application.outbox.OutboxPublisher(
                com.smxplore.activitiesevents.domain.ports.repository.OutboxPort.noop());
        var eventEvents = new com.smxplore.activitiesevents.application.outbox.EventChangedEventFactory(
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
        registerEvent = new RegisterEventUseCase(eventRepo, eventEvents, publisher);
        changeEventStatus = new ChangeEventStatusUseCase(eventRepo, eventEvents, publisher);
        estimateEventAttendance = new EstimateEventAttendanceUseCase(eventRepo);
        consultEvents = new ConsultEventsUseCase(eventRepo);
        searchEvents = new SearchEventsUseCase(eventRepo);
        viewEventDetails = new ViewEventDetailsUseCase(eventRepo);
    }

    private Event eventWith(UUID id, String name) {
        Instant startsAt = Instant.now().plusSeconds(3600);
        return Event.builder()
                .id(id)
                .siteId(UUID.randomUUID())
                .name(name)
                .category("Nautical")
                .price(new BigDecimal("150000.00"))
                .maxCapacity(12)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(7200))
                .description("Guided kayak tour at sunset.")
                .build();
    }

    private Event eventWith(UUID id) {
        return eventWith(id, "Sunset Kayak Tour");
    }

    @Test
    void shouldRegisterValidEvent() {
        UUID id = UUID.randomUUID();

        Event saved = Objects.requireNonNull(registerEvent.handle(eventWith(id)).block());

        assertEquals(id, saved.getId());
        assertTrue(Objects.requireNonNull(eventRepo.existsById(id).block()));
    }

    @Test
    void shouldFailRegisterWhenEventIsNull() {
        assertThrows(InvalidEventRequestException.class, () -> registerEvent.handle(null).block());
    }

    @Test
    void shouldPropagateValidationErrorOnRegister() {
        Event invalid = eventWith(UUID.randomUUID()).toBuilder().siteId(null).build();

        assertThrows(InvalidEventRequestException.class, () -> registerEvent.handle(invalid).block());
    }

    @Test
    void shouldChangeStatusOfExistingEvent() {
        UUID id = UUID.randomUUID();
        eventRepo.save(eventWith(id)).block();

        Event updated = Objects.requireNonNull(changeEventStatus.handle(id, EventStatus.PUBLISHED).block());

        assertEquals(EventStatus.PUBLISHED, updated.getStatus());
    }

    @Test
    void shouldFailChangingStatusWhenEventDoesNotExist() {
        assertThrows(EventNotFoundException.class,
                () -> changeEventStatus.handle(UUID.randomUUID(), EventStatus.PUBLISHED).block());
    }

    @Test
    void shouldFailChangingStatusWithInvalidTransition() {
        UUID id = UUID.randomUUID();
        eventRepo.save(eventWith(id).toBuilder().status(EventStatus.CANCELLED).build()).block();

        assertThrows(InvalidEventStatusTransitionException.class,
                () -> changeEventStatus.handle(id, EventStatus.PUBLISHED).block());
    }

    @Test
    void shouldEstimateAttendance() {
        UUID id = UUID.randomUUID();
        eventRepo.save(eventWith(id)).block();

        Event updated = Objects.requireNonNull(estimateEventAttendance.handle(id, 10).block());

        assertEquals(10, updated.getExpectedAttendance());
    }

    @Test
    void shouldFailEstimateWhenEventDoesNotExist() {
        assertThrows(EventNotFoundException.class,
                () -> estimateEventAttendance.handle(UUID.randomUUID(), 100).block());
    }

    @Test
    void shouldFailEstimateWithNegativeAttendance() {
        UUID id = UUID.randomUUID();
        eventRepo.save(eventWith(id)).block();

        assertThrows(InvalidExpectedAttendanceException.class,
                () -> estimateEventAttendance.handle(id, -1).block());
    }

    @Test
    void shouldConsultAllEvents() {
        eventRepo.save(eventWith(UUID.randomUUID(), "Sunset Kayak Tour").toBuilder().status(EventStatus.PUBLISHED).build()).block();
        eventRepo.save(eventWith(UUID.randomUUID(), "City Walking Tour").toBuilder().status(EventStatus.PUBLISHED).build()).block();

        var page = Objects.requireNonNull(consultEvents
                .handle(com.smxplore.activitiesevents.domain.model.event.EventFilter.published(0, 20)).block());

        assertEquals(2, page.content().size());
        assertEquals(2, page.total());
    }

    @Test
    void shouldFilterEventsByStatusAndCategory() {
        eventRepo.save(eventWith(UUID.randomUUID(), "Sunset Kayak Tour")
                .toBuilder().status(EventStatus.PUBLISHED).build()).block();
        eventRepo.save(eventWith(UUID.randomUUID(), "Draft Concert")
                .toBuilder().status(EventStatus.DRAFT).category("Musica").build()).block();

        var filter = new com.smxplore.activitiesevents.domain.model.event.EventFilter(
                null, EventStatus.DRAFT, "Musica", null, null, null, null, null, null, 0, 20);

        var page = Objects.requireNonNull(consultEvents.handle(filter).block());

        assertEquals(1, page.content().size());
        assertEquals("Draft Concert", page.content().getFirst().getName());
    }

    @Test
    void shouldSearchEventsByName() {
        eventRepo.save(eventWith(UUID.randomUUID(), "Sunset Kayak Tour").toBuilder().status(EventStatus.PUBLISHED).build()).block();
        eventRepo.save(eventWith(UUID.randomUUID(), "City Walking Tour").toBuilder().status(EventStatus.PUBLISHED).build()).block();

        var page = Objects.requireNonNull(searchEvents.handle("kayak", 0, 20).block());

        assertEquals(1, page.content().size());
        assertEquals("Sunset Kayak Tour", page.content().getFirst().getName());
    }

    @Test
    void shouldViewEventDetails() {
        UUID id = UUID.randomUUID();
        eventRepo.save(eventWith(id)).block();

        Event found = Objects.requireNonNull(viewEventDetails.handle(id).block());

        assertEquals(id, found.getId());
    }

    @Test
    void shouldFailViewDetailsWhenEventDoesNotExist() {
        assertThrows(EventNotFoundException.class,
                () -> viewEventDetails.handle(UUID.randomUUID()).block());
    }
}
