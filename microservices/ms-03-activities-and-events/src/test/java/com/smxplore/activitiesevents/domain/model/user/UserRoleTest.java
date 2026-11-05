package com.smxplore.activitiesevents.domain.model.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserRoleTest {

    @Test
    void mapsTheCanonicalEnglishRoleCodes() {
        assertThat(UserRole.fromClaim("TOURIST")).isEqualTo(UserRole.TOURIST);
        assertThat(UserRole.fromClaim("GUIDE")).isEqualTo(UserRole.GUIDE);
        assertThat(UserRole.fromClaim("SERVICE_PROVIDER")).isEqualTo(UserRole.SERVICE_PROVIDER);
        assertThat(UserRole.fromClaim("DESTINATION_MANAGER")).isEqualTo(UserRole.DESTINATION_MANAGER);
        assertThat(UserRole.fromClaim("ADMIN")).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void stillReadsTheLegacySpanishCodes() {
        assertThat(UserRole.fromClaim("TURISTA")).isEqualTo(UserRole.TOURIST);
        assertThat(UserRole.fromClaim("GUIA")).isEqualTo(UserRole.GUIDE);
        assertThat(UserRole.fromClaim("PRESTADORA_SERVICIOS")).isEqualTo(UserRole.SERVICE_PROVIDER);
        assertThat(UserRole.fromClaim("GESTORA_DESTINOS")).isEqualTo(UserRole.DESTINATION_MANAGER);
    }

    @Test
    void rejectsNonCanonicalSpellings() {
        assertThat(UserRole.fromClaim("ROLE_GUIDE")).isNull();
        assertThat(UserRole.fromClaim("guide")).isNull();
        assertThat(UserRole.fromClaim(" gestora_destinos ")).isNull();
    }

    @Test
    void returnsNullForMissingOrUnknownRoles() {
        assertThat(UserRole.fromClaim(null)).isNull();
        assertThat(UserRole.fromClaim("  ")).isNull();
        assertThat(UserRole.fromClaim("SUPERUSER")).isNull();
    }

    @Test
    void exposesTheEnglishCodeAndKeepsTheSpanishOneAsALegacyAlias() {
        assertThat(UserRole.TOURIST.code()).isEqualTo("TOURIST");
        assertThat(UserRole.GUIDE.code()).isEqualTo("GUIDE");
        assertThat(UserRole.SERVICE_PROVIDER.code()).isEqualTo("SERVICE_PROVIDER");
        assertThat(UserRole.DESTINATION_MANAGER.code()).isEqualTo("DESTINATION_MANAGER");
        assertThat(UserRole.ADMIN.code()).isEqualTo("ADMIN");

        assertThat(UserRole.TOURIST.legacyCode()).isEqualTo("TURISTA");
        assertThat(UserRole.GUIDE.legacyCode()).isEqualTo("GUIA");
        assertThat(UserRole.SERVICE_PROVIDER.legacyCode()).isEqualTo("PRESTADORA_SERVICIOS");
        assertThat(UserRole.DESTINATION_MANAGER.legacyCode()).isEqualTo("GESTORA_DESTINOS");
        assertThat(UserRole.ADMIN.legacyCode()).isEqualTo("ADMIN");
    }
}
