package com.smxplore.recommendations.infrastructure.adapters.in.web.security;

import com.smxplore.recommendations.domain.exception.InvalidJwtException;
import com.smxplore.recommendations.infrastructure.config.JwtProperties;
import com.smxplore.recommendations.infrastructure.security.JwtIdentityResolver;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUserResolverTest {

    private final JwtProperties properties = new JwtProperties();
    private final JwtUserResolver resolver =
            new JwtUserResolver(properties, new JwtIdentityResolver(properties));

    @Test
    void readsTheTouristIdFromTheStandardSubject() {
        UUID touristId = UUID.randomUUID();
        Jwt jwt = jwtBuilder()
                .subject(touristId.toString())
                .claim("userId", UUID.randomUUID().toString())
                .build();

        AuthenticatedUser user = resolver.resolve(jwt);

        assertThat(user.touristId()).isEqualTo(touristId);
        assertThat(user.role()).isEqualTo("TOURIST");
        assertThat(user.name()).isEqualTo("Ana");
    }

    @Test
    void fallsBackToTheUserIdClaimWhenThereIsNoSubject() {
        UUID touristId = UUID.randomUUID();
        Jwt jwt = jwtBuilder()
                .claim("userId", touristId.toString())
                .build();

        AuthenticatedUser user = resolver.resolve(jwt);

        assertThat(user.touristId()).isEqualTo(touristId);
    }

    @Test
    void rejectsATokenWithoutAnIdentity() {
        Jwt jwt = jwtBuilder().build();

        assertThatThrownBy(() -> resolver.resolve(jwt))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void rejectsANonUuidIdentity() {
        Jwt jwt = jwtBuilder().claim("userId", "not-a-uuid").build();

        assertThatThrownBy(() -> resolver.resolve(jwt))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("UUID");
    }

    private org.springframework.security.oauth2.jwt.Jwt.Builder jwtBuilder() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("role", "TOURIST")
                .claim("name", "Ana");
    }
}
