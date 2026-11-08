package com.smxplore.activitiesevents.infrastructure.adapters.in.web.security;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.user.UserRole;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ViewActivityDetailsUseCasePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Ownership guard for activity writes.
 * <p>
 * ADMIN puede con cualquier actividad; DESTINATION_MANAGER solo con las actividades de los sitios
 * que gestiona (se resuelven contra ms-04 con el token del usuario); GUIDE solo con las suyas.
 * Cualquier otro rol se rechaza. El rol se lee del mismo claim configurable que usa la cadena de
 * seguridad.
 */
@Component
public class ActivityAccessGuard {

    private final ViewActivityDetailsUseCasePort viewActivityDetailsUseCase;
    private final SiteCatalogPort siteCatalog;
    private final String roleClaim;

    public ActivityAccessGuard(ViewActivityDetailsUseCasePort viewActivityDetailsUseCase,
                               SiteCatalogPort siteCatalog,
                               @Value("${app.security.jwt.role-claim:role}") String roleClaim) {
        this.viewActivityDetailsUseCase = viewActivityDetailsUseCase;
        this.siteCatalog = siteCatalog;
        this.roleClaim = roleClaim;
    }

    public boolean isGuide(Jwt jwt) {
        return role(jwt) == UserRole.GUIDE;
    }

    /** Admins y gestoras pueden ver el catálogo completo, publicado o no. */
    public boolean isStaff(Jwt jwt) {
        return isStaffRole(role(jwt));
    }

    /** Solo el admin puede enumerar borradores de cualquier guía. */
    public boolean isAdmin(Jwt jwt) {
        return role(jwt) == UserRole.ADMIN;
    }

    /** Staff y el guía de la actividad pueden leerla en cualquier estado. */
    public boolean canViewUnpublished(Activity activity, Jwt jwt) {
        var role = role(jwt);
        if (role == UserRole.ADMIN || role == UserRole.DESTINATION_MANAGER) {
            return true;
        }
        return role == UserRole.GUIDE
                && jwt != null
                && jwt.getSubject() != null
                && activity.getGuideId() != null
                && activity.getGuideId().toString().equals(jwt.getSubject());
    }

    /**
     * Autoriza una escritura sobre una actividad concreta: el gestor solo entra si el sitio de la
     * actividad está entre los que gestiona en ms-04 (no se filtra la existencia de recursos ajenos:
     * la actividad no existe → 404; existe pero es de otro → 403).
     */
    public Mono<Void> requireCanManage(UUID activityId, Jwt jwt) {
        if (jwt == null) {
            return unauthorized();
        }
        var role = role(jwt);
        if (role == UserRole.ADMIN) {
            return Mono.empty();
        }
        if (role != UserRole.DESTINATION_MANAGER && role != UserRole.GUIDE) {
            return forbid("Only an admin, the destination manager of the site or the guide of record"
                    + " can manage an activity.");
        }
        return viewActivityDetailsUseCase.handle(activityId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found")))
                .flatMap(activity -> role == UserRole.DESTINATION_MANAGER
                        ? requireManagesSite(activity.getSiteId(), jwt)
                        : requireIsGuideOfRecord(activity, jwt));
    }

    /**
     * Autoriza la creación de una actividad. El admin puede cualquier sitio; el gestor solo los
     * suyos y, al no existir un directorio de usuarios en este servicio con el que verificar que un
     * guía esté autorizado en el sitio, se rechaza cualquier {@code guideId} que venga en el body
     * (fallar cerrado). El guía siempre se atribuye a sí mismo desde el token.
     */
    public Mono<Void> requireCanCreate(UUID siteId, UUID requestedGuideId, Jwt jwt) {
        if (jwt == null) {
            return unauthorized();
        }
        var role = role(jwt);
        if (role == UserRole.ADMIN || role == UserRole.GUIDE) {
            return Mono.empty();
        }
        if (role != UserRole.DESTINATION_MANAGER) {
            return forbid("Only an admin, a destination manager or a guide can create an activity.");
        }
        return requireManagesSite(siteId, jwt)
                .then(requestedGuideId == null
                        ? Mono.<Void>empty()
                        : Mono.error(forbidden("A destination manager cannot assign a guide while creating an"
                                + " activity: guide authorization on the site cannot be verified.")));
    }

    private Mono<Void> requireIsGuideOfRecord(Activity activity, Jwt jwt) {
        return owns(activity, jwt)
                ? Mono.empty()
                : Mono.error(forbidden("Cannot manage another guide's activity"));
    }

    /** El sitio debe estar entre los que ms-04 devuelve para el gestor del token. */
    private Mono<Void> requireManagesSite(UUID siteId, Jwt jwt) {
        if (siteId == null) {
            return forbid("A site is required to manage an activity.");
        }
        var subject = subject(jwt);
        if (subject == null) {
            return forbid("Invalid token subject.");
        }
        return siteCatalog.findByManagerId(subject, jwt.getTokenValue())
                .map(site -> site.siteId())
                .filter(managedSiteId -> siteId.equals(managedSiteId))
                .hasElements()
                .flatMap(owns -> owns
                        ? Mono.<Void>empty()
                        : Mono.error(forbidden("Cannot manage an activity of another destination.")))
                .onErrorMap(error -> error instanceof ResponseStatusException ? error
                        : new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                "Cannot verify the site ownership right now.", error));
    }

    private boolean owns(Activity activity, Jwt jwt) {
        var subject = jwt.getSubject();
        if (subject == null) {
            return false;
        }
        try {
            return UUID.fromString(subject).equals(activity.getGuideId());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static UUID subject(Jwt jwt) {
        var subject = jwt.getSubject();
        if (subject == null) {
            return null;
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean isStaffRole(UserRole role) {
        return role == UserRole.ADMIN || role == UserRole.DESTINATION_MANAGER;
    }

    private static Mono<Void> unauthorized() {
        return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required."));
    }

    private static Mono<Void> forbid(String message) {
        return Mono.error(forbidden(message));
    }

    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private UserRole role(Jwt jwt) {
        return jwt == null ? null : UserRole.fromClaim(jwt.getClaimAsString(roleClaim));
    }
}
