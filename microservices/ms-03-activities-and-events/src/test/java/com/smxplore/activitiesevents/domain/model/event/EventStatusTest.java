package com.smxplore.activitiesevents.domain.model.event;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventStatusTest {

    @Test
    void shouldAllowPublishingDraftEvent() {
        assertTrue(EventStatus.DRAFT.canTransitionTo(EventStatus.PUBLISHED));
    }

    @Test
    void shouldAllowCancellingDraftEvent() {
        assertTrue(EventStatus.DRAFT.canTransitionTo(EventStatus.CANCELLED));
    }

    @Test
    void shouldAllowPostponingPublishedEvent() {
        assertTrue(EventStatus.PUBLISHED.canTransitionTo(EventStatus.POSTPONED));
    }

    @Test
    void shouldAllowCancellingPublishedEvent() {
        assertTrue(EventStatus.PUBLISHED.canTransitionTo(EventStatus.CANCELLED));
    }

    @Test
    void shouldAllowRepublishingPostponedEvent() {
        assertTrue(EventStatus.POSTPONED.canTransitionTo(EventStatus.PUBLISHED));
    }

    @Test
    void shouldAllowCancellingPostponedEvent() {
        assertTrue(EventStatus.POSTPONED.canTransitionTo(EventStatus.CANCELLED));
    }

    @Test
    void shouldNotTransitionFromCancelledEvent() {
        assertFalse(EventStatus.CANCELLED.canTransitionTo(EventStatus.PUBLISHED));
        assertFalse(EventStatus.CANCELLED.canTransitionTo(EventStatus.POSTPONED));
        assertFalse(EventStatus.CANCELLED.canTransitionTo(EventStatus.DRAFT));
    }

    @Test
    void shouldNotAllowSameStateTransition() {
        for (EventStatus status : EventStatus.values()) {
            assertFalse(status.canTransitionTo(status));
        }
    }

    @Test
    void shouldNotAllowTransitionToNullStatus() {
        for (EventStatus status : EventStatus.values()) {
            assertFalse(status.canTransitionTo(null));
        }
    }

    @Test
    void shouldExposePinnedTransitions() {
        assertEquals(Set.of(EventStatus.PUBLISHED, EventStatus.CANCELLED), EventStatus.DRAFT.allowedTransitions());
        assertEquals(Set.of(EventStatus.POSTPONED, EventStatus.CANCELLED), EventStatus.PUBLISHED.allowedTransitions());
        assertEquals(Set.of(EventStatus.PUBLISHED, EventStatus.CANCELLED), EventStatus.POSTPONED.allowedTransitions());
        assertEquals(Set.of(), EventStatus.CANCELLED.allowedTransitions());
    }

    @Test
    void shouldMarkCancelledAsFinal() {
        assertTrue(EventStatus.CANCELLED.isFinal());
        assertFalse(EventStatus.DRAFT.isFinal());
        assertFalse(EventStatus.PUBLISHED.isFinal());
        assertFalse(EventStatus.POSTPONED.isFinal());
    }
}
