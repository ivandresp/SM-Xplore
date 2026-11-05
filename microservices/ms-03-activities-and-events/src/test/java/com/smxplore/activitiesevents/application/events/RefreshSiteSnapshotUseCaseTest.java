package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.application.events.RefreshSiteSnapshotUseCase.SiteChanged;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventRequestException;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RefreshSiteSnapshotUseCaseTest {

    private final EventRepositoryPort eventRepo = mock(EventRepositoryPort.class);
    private final RefreshSiteSnapshotUseCase useCase = new RefreshSiteSnapshotUseCase(eventRepo);

    @Test
    void forwardsTheSiteChangeToTheRepository() {
        var siteId = UUID.randomUUID();
        var managerId = UUID.randomUUID();
        var updatedAt = Instant.parse("2026-11-05T03:00:00Z");
        given(eventRepo.applySiteSnapshot(siteId, "Playa Rosa", managerId, updatedAt))
                .willReturn(Mono.just(2));

        StepVerifier.create(useCase.handle(new SiteChanged(siteId, "Playa Rosa", managerId, updatedAt)))
                .expectNext(2)
                .verifyComplete();

        verify(eventRepo).applySiteSnapshot(siteId, "Playa Rosa", managerId, updatedAt);
    }

    @Test
    void requiresTheManager() {
        assertThatThrownBy(() -> new SiteChanged(UUID.randomUUID(), "Playa Rosa", null, Instant.now()))
                .isInstanceOf(InvalidEventRequestException.class)
                .hasMessageContaining("manager");
    }

    @Test
    void requiresTheUpdateInstant() {
        assertThatThrownBy(() -> new SiteChanged(UUID.randomUUID(), "Playa Rosa", UUID.randomUUID(), null))
                .isInstanceOf(InvalidEventRequestException.class)
                .hasMessageContaining("update");
    }
}
