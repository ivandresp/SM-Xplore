package com.smxplore.recommendations.infrastructure.security;

import com.smxplore.recommendations.domain.exception.InvalidJwtException;
import com.smxplore.recommendations.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MS05-14: una única regla de identidad para {@code sub} y el claim configurable.
 */
class JwtIdentityResolverTest {

    private final JwtProperties properties = new JwtProperties();
    private final JwtIdentityResolver resolver = new JwtIdentityResolver(properties);

    @Test
    void prefersTheSubjectOverTheConfiguredClaim() {
        UUID subject = UUID.randomUUID();
        Jwt jwt = jwt()
                .subject(subject.toString())
                .claim("userId", UUID.randomUUID().toString())
                .build();

        assertThat(resolver.userIdOrNull(jwt)).isEqualTo(subject);
        assertThat(resolver.requireUserId(jwt)).isEqualTo(subject);
    }

    @Test
    void fallsBackToTheConfiguredUserIdClaim() {
        UUID claim = UUID.randomUUID();
        Jwt jwt = jwt().claim("userId", claim.toString()).build();

        assertThat(resolver.userIdOrNull(jwt)).isEqualTo(claim);
        assertThat(resolver.requireUserId(jwt)).isEqualTo(claim);
    }

    @Test
    void honoursACustomConfiguredClaimName() {
        properties.setUserIdClaim("subject_id");
        UUID claim = UUID.randomUUID();
        Jwt jwt = jwt()
                .claim("subject_id", claim.toString())
                .claim("userId", UUID.randomUUID().toString())
                .build();

        assertThat(resolver.userIdOrNull(jwt)).isEqualTo(claim);
    }

    @Test
    void returnsNullWhenTheIdentityIsMissingOrNotAUuid() {
        assertThat(resolver.userIdOrNull(jwt().build())).isNull();
        assertThat(resolver.userIdOrNull(jwt().claim("userId", "not-a-uuid").build())).isNull();
    }

    @Test
    void requireUserIdFailsWithTheConfiguredClaimNameInTheMessage() {
        properties.setUserIdClaim("subject_id");

        assertThatThrownBy(() -> resolver.requireUserId(jwt().build()))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("subject_id");
        assertThatThrownBy(() -> resolver.requireUserId(jwt().claim("subject_id", "nope").build()))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("UUID");
    }

    private Jwt.Builder jwt() {
        return Jwt.withTokenValue("token").header("alg", "RS256").claim("role", "TOURIST");
    }
}
