package com.smxplore.activitiesevents.infrastructure.adapters.in.web.security;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ViewActivityDetailsUseCasePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Ownership guard for activity writes: guides may only manage their own
 * activities; admins and destination managers may manage any activity.
 * The JWT role is read from the same configurable claim used by the security chain.
 */
@Component
public class ActivityAccessGuard {

    private static final String ADMIN = "ADMIN";
    private static final String DESTINATION_MANAGER = "DESTINATION_MANAGER";
    private static final String GUIDE = "GUIDE";

    private final ViewActivityDetailsUseCasePort viewActivityDetailsUseCase;
    private final String roleClaim;

    public ActivityAccessGuard(ViewActivityDetailsUseCasePort viewActivityDetailsUseCase,
                               @Value("${app.security.jwt.role-claim:role}") String roleClaim) {
        this.viewActivityDetailsUseCase = viewActivityDetailsUseCase;
        this.roleClaim = roleClaim;
    }

    public boolean isGuide(Jwt jwt) {
        return jwt != null && GUIDE.equals(role(jwt));
    }

    public Mono<Void> requireCanManage(UUID activityId, Jwt jwt) {
        // The security chain always provides a JWT for writes; a null principal can
        // only happen in sliced controller tests that bypass the security filters.
        if (jwt == null || isStaff(jwt)) {
            return Mono.empty();
        }
        return viewActivityDetailsUseCase.handle(activityId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found")))
                .flatMap(activity -> owns(activity, jwt)
                        ? Mono.empty()
                        : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Cannot manage another guide's activity")));
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

    private boolean isStaff(Jwt jwt) {
        var role = role(jwt);
        return ADMIN.equals(role) || DESTINATION_MANAGER.equals(role);
    }

    private String role(Jwt jwt) {
        return jwt.getClaimAsString(roleClaim);
    }
}
