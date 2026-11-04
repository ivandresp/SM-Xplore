package com.smxplore.sitesandservices.infrastructure.adapters.in.web.security;

import com.smxplore.sitesandservices.domain.exception.InvalidJwtException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUserResolverTest {

    private final JwtUserResolver resolver = new JwtUserResolver(new JwtProperties());

    @Test
    void readsIdentityAndRoleFromTheStandardClaims() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("role", "GESTORA_DESTINOS")
                .claim("name", "Gestora")
                .build();

        Actor actor = resolver.resolve(jwt);

        assertThat(actor.userId()).isEqualTo(userId);
        assertThat(actor.role()).isEqualTo(UserRole.GESTORA_DESTINOS);
        assertThat(actor.name()).isEqualTo("Gestora");
    }

    @Test
    void fallsBackToUserIdAndNormalizesRolePrefix() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("userId", userId.toString())
                .claim("role", "ROLE_TURISTA")
                .build();

        Actor actor = resolver.resolve(jwt);

        assertThat(actor.userId()).isEqualTo(userId);
        assertThat(actor.role()).isEqualTo(UserRole.TURISTA);
    }

    @Test
    void rejectsAnUnknownRole() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .claim("role", "ROLE_UNKNOWN")
                .build();

        assertThatThrownBy(() -> resolver.resolve(jwt))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("Unsupported JWT role");
    }
}
