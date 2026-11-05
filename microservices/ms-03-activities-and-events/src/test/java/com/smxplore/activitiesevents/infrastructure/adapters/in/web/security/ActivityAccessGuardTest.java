package com.smxplore.activitiesevents.infrastructure.adapters.in.web.security;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityAccessGuardTest {

    private static final UUID ACTIVITY_ID = UUID.randomUUID();
    private static final UUID GUIDE_ID = UUID.randomUUID();
    private static final Activity ACTIVITY = Activity.builder()
            .id(ACTIVITY_ID)
            .siteId(UUID.randomUUID())
            .guideId(GUIDE_ID)
            .name("Kayak")
            .category("Nautical")
            .maxCapacity(10)
            .durationMinutes(60)
            .build();

    private final ActivityAccessGuard guard = new ActivityAccessGuard(
            activityId -> Mono.just(ACTIVITY), "role");

    @Test
    void nullPrincipalIsUnauthorized() {
        StepVerifier.create(guard.requireCanManage(ACTIVITY_ID, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.UNAUTHORIZED);
                })
                .verify();
    }

    @Test
    void anotherGuideIsForbidden() {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .claim("role", "GUIDE")
                .build();

        StepVerifier.create(guard.requireCanManage(ACTIVITY_ID, jwt))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN);
                })
                .verify();
    }
}
