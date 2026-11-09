package com.smxplore.activitiesevents.infrastructure.adapters.in.web.security;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityAccessGuardTest {

    private static final UUID ACTIVITY_ID = UUID.randomUUID();
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final UUID GUIDE_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final Activity ACTIVITY = Activity.builder()
            .id(ACTIVITY_ID)
            .siteId(SITE_ID)
            .guideId(GUIDE_ID)
            .name("Kayak")
            .category("Nautical")
            .maxCapacity(10)
            .durationMinutes(60)
            .build();

    /** Catálogo de ms-04 simulado: solo MANAGER_ID gestiona SITE_ID. */
    private static final SiteCatalogPort CATALOG = new SiteCatalogPort() {
        @Override
        public Mono<SiteSnapshot> findById(UUID siteId, String bearerToken) {
            return SITE_ID.equals(siteId)
                    ? Mono.just(new SiteSnapshot(SITE_ID, MANAGER_ID, "Bahia", null, null, null))
                    : Mono.empty();
        }

        @Override
        public Flux<SiteSnapshot> findByManagerId(UUID managerId, String bearerToken) {
            return MANAGER_ID.equals(managerId)
                    ? Flux.just(new SiteSnapshot(SITE_ID, MANAGER_ID, "Bahia", null, null, null))
                    : Flux.empty();
        }

        @Override
        public Flux<SiteSnapshot> findByZoneId(UUID zoneId, String bearerToken) {
            return Flux.empty();
        }
    };

    private final ActivityAccessGuard guard = new ActivityAccessGuard(activityId -> Mono.just(ACTIVITY), CATALOG, "role");

    @Test
    void nullPrincipalIsUnauthorized() {
        StepVerifier.create(guard.requireCanManage(ACTIVITY_ID, null))
                .expectErrorSatisfies(error -> assertStatus(error, HttpStatus.UNAUTHORIZED))
                .verify();
    }

    @Test
    void anotherGuideIsForbidden() {
        StepVerifier.create(guard.requireCanManage(ACTIVITY_ID, jwt(UUID.randomUUID(), "GUIDE")))
                .expectErrorSatisfies(error -> assertStatus(error, HttpStatus.FORBIDDEN))
                .verify();
    }

    @Test
    void guideOfRecordCanManage() {
        StepVerifier.create(guard.requireCanManage(ACTIVITY_ID, jwt(GUIDE_ID, "GUIDE")))
                .verifyComplete();
    }

    @Test
    void adminCanManageAnyActivity() {
        StepVerifier.create(guard.requireCanManage(ACTIVITY_ID, jwt(UUID.randomUUID(), "ADMIN")))
                .verifyComplete();
    }

    @Test
    void managerOfTheSiteCanManage() {
        StepVerifier.create(guard.requireCanManage(ACTIVITY_ID, jwt(MANAGER_ID, "DESTINATION_MANAGER")))
                .verifyComplete();
    }

    @Test
    void managerOfAnotherSiteIsForbidden() {
        StepVerifier.create(guard.requireCanManage(ACTIVITY_ID, jwt(UUID.randomUUID(), "DESTINATION_MANAGER")))
                .expectErrorSatisfies(error -> assertStatus(error, HttpStatus.FORBIDDEN))
                .verify();
    }

    @Test
    void managerWithInvalidSubjectIsForbidden() {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("not-a-uuid")
                .claim("role", "DESTINATION_MANAGER")
                .build();

        StepVerifier.create(guard.requireCanManage(ACTIVITY_ID, jwt))
                .expectErrorSatisfies(error -> assertStatus(error, HttpStatus.FORBIDDEN))
                .verify();
    }

    @Test
    void missingActivityIsNotFound() {
        var missingGuard = new ActivityAccessGuard(activityId -> Mono.empty(), CATALOG, "role");

        StepVerifier.create(missingGuard.requireCanManage(ACTIVITY_ID, jwt(GUIDE_ID, "GUIDE")))
                .expectErrorSatisfies(error -> assertStatus(error, HttpStatus.NOT_FOUND))
                .verify();
    }

    @Test
    void managerCanCreateOnASiteItManages() {
        StepVerifier.create(guard.requireCanCreate(SITE_ID, null, jwt(MANAGER_ID, "DESTINATION_MANAGER")))
                .verifyComplete();
    }

    @Test
    void managerCannotCreateOnAnotherSite() {
        StepVerifier.create(guard.requireCanCreate(UUID.randomUUID(), null, jwt(MANAGER_ID, "DESTINATION_MANAGER")))
                .expectErrorSatisfies(error -> assertStatus(error, HttpStatus.FORBIDDEN))
                .verify();
    }

    @Test
    void managerCannotAssignAGuideOnCreation() {
        StepVerifier.create(guard.requireCanCreate(SITE_ID, GUIDE_ID, jwt(MANAGER_ID, "DESTINATION_MANAGER")))
                .expectErrorSatisfies(error -> assertStatus(error, HttpStatus.FORBIDDEN))
                .verify();
    }

    @Test
    void adminCanCreateWithAnyGuide() {
        StepVerifier.create(guard.requireCanCreate(SITE_ID, GUIDE_ID, jwt(UUID.randomUUID(), "ADMIN")))
                .verifyComplete();
    }

    @Test
    void guideCannotCreateOnAMissingSite() {
        StepVerifier.create(guard.requireCanCreate(UUID.randomUUID(), null, jwt(GUIDE_ID, "GUIDE")))
                .expectErrorSatisfies(error -> assertStatus(error, HttpStatus.NOT_FOUND))
                .verify();
    }

    @Test
    void guideCanCreateOnAnExistingSite() {
        StepVerifier.create(guard.requireCanCreate(SITE_ID, null, jwt(GUIDE_ID, "GUIDE")))
                .verifyComplete();
    }

    @Test
    void touristCannotCreate() {
        StepVerifier.create(guard.requireCanCreate(SITE_ID, null, jwt(UUID.randomUUID(), "TOURIST")))
                .expectErrorSatisfies(error -> assertStatus(error, HttpStatus.FORBIDDEN))
                .verify();
    }

    private static Jwt jwt(UUID subject, String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject.toString())
                .claim("role", role)
                .build();
    }

    private static void assertStatus(Throwable error, HttpStatus status) {
        assertThat(error).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(status);
    }
}
