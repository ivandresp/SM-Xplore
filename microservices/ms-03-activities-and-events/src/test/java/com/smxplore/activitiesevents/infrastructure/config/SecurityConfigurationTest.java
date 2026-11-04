package com.smxplore.activitiesevents.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationTest {

    @Test
    void mapsTheSpanishRoleCodeToTheInternalEnglishAuthority() {
        var converter = new SecurityConfiguration().jwtAuthenticationConverter(new JwtProperties());
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .claim("role", "GESTORA_DESTINOS")
                .build();

        var authentication = converter.convert(jwt).block();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_DESTINATION_MANAGER");
    }

    @Test
    void grantsNoAuthorityWhenTheRoleIsUnknown() {
        var converter = new SecurityConfiguration().jwtAuthenticationConverter(new JwtProperties());
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .claim("role", "SUPERUSER")
                .build();

        var authentication = converter.convert(jwt).block();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities()).isEmpty();
    }
}
