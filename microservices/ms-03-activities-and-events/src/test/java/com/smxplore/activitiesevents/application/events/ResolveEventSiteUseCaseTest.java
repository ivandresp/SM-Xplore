package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.domain.exceptions.event.EventSiteAccessDeniedException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventRequestException;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.user.UserRole;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResolveEventSiteUseCaseTest {

    private static final UUID SITE_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();

    private final SiteCatalogPort sites = new SiteCatalogPort() {
        @Override
        public Mono<SiteSnapshot> findById(UUID siteId, String token) {
            return siteId.equals(SITE_ID)
                    ? Mono.just(new SiteSnapshot(SITE_ID, MANAGER_ID, "Parque Tayrona"))
                    : Mono.empty();
        }

        @Override
        public Flux<SiteSnapshot> findByManagerId(UUID managerId, String token) {
            return Flux.empty();
        }
    };
    private final ResolveEventSiteUseCase useCase = new ResolveEventSiteUseCase(sites);

    @Test
    void managerCanOnlyUseTheirOwnSiteAndTheSiteOwnsTheSnapshot() {
        var event = event();

        StepVerifier.create(useCase.handle(event, MANAGER_ID, UserRole.DESTINATION_MANAGER, "token"))
                .assertNext(resolved -> {
                    assertThat(resolved.getManagerId()).isEqualTo(MANAGER_ID);
                    assertThat(resolved.getSiteName()).isEqualTo("Parque Tayrona");
                })
                .verifyComplete();
    }

    @Test
    void managerCannotUseAnotherManagersSite() {
        StepVerifier.create(useCase.handle(event(), UUID.randomUUID(), UserRole.DESTINATION_MANAGER, "token"))
                .expectError(EventSiteAccessDeniedException.class)
                .verify();
    }

    @Test
    void adminUsesTheSiteManagerInsteadOfTheBody() {
        StepVerifier.create(useCase.handle(event(), UUID.randomUUID(), UserRole.ADMIN, "token"))
                .assertNext(resolved -> assertThat(resolved.getManagerId()).isEqualTo(MANAGER_ID))
                .verifyComplete();
    }

    @Test
    void missingSiteIsRejected() {
        var event = Event.builder()
                .id(UUID.randomUUID())
                .siteId(UUID.randomUUID())
                .name("Sunset Kayak")
                .category("Nautical")
                .build();

        StepVerifier.create(useCase.handle(event, MANAGER_ID, UserRole.ADMIN, "token"))
                .expectError(InvalidEventRequestException.class)
                .verify();
    }

    private static Event event() {
        return Event.builder()
                .id(UUID.randomUUID())
                .siteId(SITE_ID)
                .name("Sunset Kayak")
                .category("Nautical")
                .build();
    }
}
