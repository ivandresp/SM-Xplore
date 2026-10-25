package com.smxplore.activitiesevents.domain.model.activity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityStatusTest {

    @Test
    void shouldAllowPublishingDraftActivity() {
        assertTrue(ActivityStatus.DRAFT.canTransitionTo(ActivityStatus.PUBLISHED));
    }

    @Test
    void shouldAllowCancellingDraftActivity() {
        assertTrue(ActivityStatus.DRAFT.canTransitionTo(ActivityStatus.CANCELLED));
    }

    @Test
    void shouldAllowPausingPublishedActivity() {
        assertTrue(ActivityStatus.PUBLISHED.canTransitionTo(ActivityStatus.PAUSED));
    }

    @Test
    void shouldAllowCancellingPublishedActivity() {
        assertTrue(ActivityStatus.PUBLISHED.canTransitionTo(ActivityStatus.CANCELLED));
    }

    @Test
    void shouldAllowRepublishingPausedActivity() {
        assertTrue(ActivityStatus.PAUSED.canTransitionTo(ActivityStatus.PUBLISHED));
    }

    @Test
    void shouldAllowCancellingPausedActivity() {
        assertTrue(ActivityStatus.PAUSED.canTransitionTo(ActivityStatus.CANCELLED));
    }

    @Test
    void shouldNotTransitionFromCancelledActivity() {
        assertFalse(ActivityStatus.CANCELLED.canTransitionTo(ActivityStatus.PUBLISHED));
        assertFalse(ActivityStatus.CANCELLED.canTransitionTo(ActivityStatus.PAUSED));
        assertFalse(ActivityStatus.CANCELLED.canTransitionTo(ActivityStatus.DRAFT));
    }

    @Test
    void shouldNotAllowSameStateTransition() {
        for (ActivityStatus status : ActivityStatus.values()) {
            assertFalse(status.canTransitionTo(status));
        }
    }

    @Test
    void shouldNotAllowTransitionToNullStatus() {
        for (ActivityStatus status : ActivityStatus.values()) {
            assertFalse(status.canTransitionTo(null));
        }
    }

    @Test
    void shouldExposePinnedTransitions() {
        assertEquals(Set.of(ActivityStatus.PUBLISHED, ActivityStatus.CANCELLED), ActivityStatus.DRAFT.allowedTransitions());
        assertEquals(Set.of(ActivityStatus.PAUSED, ActivityStatus.CANCELLED), ActivityStatus.PUBLISHED.allowedTransitions());
        assertEquals(Set.of(ActivityStatus.PUBLISHED, ActivityStatus.CANCELLED), ActivityStatus.PAUSED.allowedTransitions());
        assertEquals(Set.of(), ActivityStatus.CANCELLED.allowedTransitions());
    }

    @Test
    void shouldMarkCancelledAsFinal() {
        assertTrue(ActivityStatus.CANCELLED.isFinal());
        assertFalse(ActivityStatus.DRAFT.isFinal());
        assertFalse(ActivityStatus.PUBLISHED.isFinal());
        assertFalse(ActivityStatus.PAUSED.isFinal());
    }
}
