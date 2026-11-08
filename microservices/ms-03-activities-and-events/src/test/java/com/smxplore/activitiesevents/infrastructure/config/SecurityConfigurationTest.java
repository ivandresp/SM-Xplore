package com.smxplore.activitiesevents.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationTest {

    @Test
    void mapsTheEnglishRoleClaimToTheSpringAuthority() {
        var converter = new SecurityConfiguration().jwtAuthenticationConverter(new JwtProperties());
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .claim("role", "DESTINATION_MANAGER")
                .build();

        var authentication = converter.convert(jwt).block();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_DESTINATION_MANAGER");
    }

    @Test
    void stillMapsTheLegacySpanishRoleClaim() {
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
    @Test
    void deniesActuatorMetricsToAnonymousCallersOutsideDevelopmentProfiles() {
        var decision = SecurityConfiguration.metricsAccess(Mono.empty(), false).block();

        assertThat(decision).isNotNull();
        assertThat(decision.isGranted()).isFalse();
    }

    @Test
    void deniesActuatorMetricsToNonAdminRolesOutsideDevelopmentProfiles() {
        var guide = new UsernamePasswordAuthenticationToken("guide", "token",
                List.of(new SimpleGrantedAuthority("ROLE_GUIDE")));

        var decision = SecurityConfiguration.metricsAccess(Mono.just(guide), false).block();

        assertThat(decision).isNotNull();
        assertThat(decision.isGranted()).isFalse();
    }

    @Test
    void letsAnAdminReadActuatorMetricsOutsideDevelopmentProfiles() {
        var admin = new UsernamePasswordAuthenticationToken("admin", "token",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        var decision = SecurityConfiguration.metricsAccess(Mono.just(admin), false).block();

        assertThat(decision).isNotNull();
        assertThat(decision.isGranted()).isTrue();
    }

    @Test
    void keepsActuatorMetricsPublicInDevelopmentProfiles() {
        var decision = SecurityConfiguration.metricsAccess(Mono.empty(), true).block();

        assertThat(decision).isNotNull();
        assertThat(decision.isGranted()).isTrue();
    }
}