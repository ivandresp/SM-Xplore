package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.domain.exceptions.event.EventSiteAccessDeniedException;
import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventRequestException;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.user.UserRole;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Resolves the event site against ms-04 before persisting. The site, not the request
 * body, owns the manager snapshot; a destination manager can only use their own sites.
 */
@RequiredArgsConstructor
public class ResolveEventSiteUseCase {

    private final SiteCatalogPort siteCatalog;

    public Mono<Event> handle(Event event, UUID requesterId, UserRole requesterRole) {
        return siteCatalog.findById(event.getSiteId())
                .switchIfEmpty(Mono.error(new InvalidEventRequestException(
                        "Site %s does not exist.".formatted(event.getSiteId()))))
                .flatMap(site -> {
                    if (requesterRole == UserRole.DESTINATION_MANAGER
                            && !requesterId.equals(site.managerId())) {
                        return Mono.error(new EventSiteAccessDeniedException(
                                "The site does not belong to the authenticated manager."));
                    }
                    return Mono.just(event.toBuilder()
                            .siteName(site.name())
                            .managerId(site.managerId())
                            .build());
                });
    }
}
