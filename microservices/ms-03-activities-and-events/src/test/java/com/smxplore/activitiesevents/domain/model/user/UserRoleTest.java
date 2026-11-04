package com.smxplore.activitiesevents.domain.model.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserRoleTest {

    @Test
    void mapsTheCanonicalSpanishRoleCodes() {
        assertThat(UserRole.fromClaim("TURISTA")).isEqualTo(UserRole.TOURIST);
        assertThat(UserRole.fromClaim("GUIA")).isEqualTo(UserRole.GUIDE);
        assertThat(UserRole.fromClaim("PRESTADORA_SERVICIOS")).isEqualTo(UserRole.SERVICE_PROVIDER);
        assertThat(UserRole.fromClaim("GESTORA_DESTINOS")).isEqualTo(UserRole.DESTINATION_MANAGER);
        assertThat(UserRole.fromClaim("ADMIN")).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void rejectsNonCanonicalSpellings() {
        assertThat(UserRole.fromClaim("ROLE_GUIA")).isNull();
        assertThat(UserRole.fromClaim("guia")).isNull();
        assertThat(UserRole.fromClaim(" gestora_destinos ")).isNull();
    }

    @Test
    void acceptsOnlyTheExplicitLegacyEnglishAliases() {
        assertThat(UserRole.fromClaim("TOURIST")).isEqualTo(UserRole.TOURIST);
        assertThat(UserRole.fromClaim("DESTINATION_MANAGER")).isEqualTo(UserRole.DESTINATION_MANAGER);
        assertThat(UserRole.fromClaim("tourist")).isNull();
    }

    @Test
    void returnsNullForMissingOrUnknownRoles() {
        assertThat(UserRole.fromClaim(null)).isNull();
        assertThat(UserRole.fromClaim("  ")).isNull();
        assertThat(UserRole.fromClaim("SUPERUSER")).isNull();
    }

    @Test
    void exposesTheSpanishCodeOfEachRole() {
        assertThat(UserRole.TOURIST.code()).isEqualTo("TURISTA");
        assertThat(UserRole.GUIDE.code()).isEqualTo("GUIA");
        assertThat(UserRole.SERVICE_PROVIDER.code()).isEqualTo("PRESTADORA_SERVICIOS");
        assertThat(UserRole.DESTINATION_MANAGER.code()).isEqualTo("GESTORA_DESTINOS");
        assertThat(UserRole.ADMIN.code()).isEqualTo("ADMIN");
    }
}
