package com.smxplore.activitiesevents.infrastructure.adapters.in.web.security;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.user.UserRole;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Oculta la ocupacion de fichas que aun no son publicas. Sin token, o con un token que no sea el
 * admin ni el dueno del recurso, se responde 404 (no 403) para no confirmar que el borrador
 * existe: los listados publicos ya filtran por estado y esto cierra el acceso directo por id.
 */
@Component
@RequiredArgsConstructor
public class ItemVisibilityGuard {

    private final ActivityRepositoryPort activityRepository;
    private final EventRepositoryPort eventRepository;

    @Value("${app.security.jwt.role-claim:role}")
    private String roleClaim;

    /**
     * Deja pasar cuando el item es visible publicamente o cuando quien consulta puede verlo:
     * ADMIN siempre, y el guia/gestor de registro solo para su propio recurso.
     */
    public Mono<Void> requireVisible(UUID itemId, ReservationItemType itemType, Jwt jwt) {
        return switch (itemType) {
            case EVENT -> eventRepository.findById(itemId)
                    .switchIfEmpty(Mono.error(new EventNotFoundException(itemId)))
                    .flatMap(event -> event.getStatus() == EventStatus.PUBLISHED
                            ? Mono.empty()
                            : requireUnpublishedAccess(event.getManagerId(), jwt));
            case ACTIVITY -> activityRepository.findById(itemId)
                    .switchIfEmpty(Mono.error(new ActivityNotFoundException(itemId)))
                    .flatMap(activity -> activity.getStatus() == ActivityStatus.PUBLISHED
                            ? Mono.empty()
                            : requireUnpublishedAccess(activity.getGuideId(), jwt));
        };
    }

    private Mono<Void> requireUnpublishedAccess(UUID ownerId, Jwt jwt) {
        if (jwt == null) {
            return notFound();
        }
        if (UserRole.fromClaim(jwt.getClaimAsString(roleClaim)) == UserRole.ADMIN) {
            return Mono.empty();
        }
        return ownerId != null && ownerId.toString().equals(jwt.getSubject())
                ? Mono.empty()
                : notFound();
    }

    private Mono<Void> notFound() {
        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found."));
    }
}
