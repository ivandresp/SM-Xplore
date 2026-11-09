package com.smxplore.users.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.infrastructure.config.BootstrapAdminProperties;
import com.smxplore.users.infrastructure.config.CorsProperties;
import com.smxplore.users.infrastructure.config.InternalApiProperties;
import com.smxplore.users.infrastructure.config.JwtProperties;
import com.smxplore.users.infrastructure.config.MfaProperties;
import com.smxplore.users.infrastructure.config.SecurityHeadersProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionSecurityValidatorTest {

    private static final CorsProperties CORS = new CorsProperties(List.of("https://app.smxplore.com"));
    private static final SecurityHeadersProperties HEADERS = new SecurityHeadersProperties(true, 365);
    private static final InternalApiProperties INTERNAL =
            new InternalApiProperties("clave-interna", List.of(), Duration.ofMinutes(5), 60);

    @Test
    void acceptsASecureConfiguration() {
        assertThatCode(() -> validator(jwt("pem", false, false), admin(false, "Segura123456"), mfa(true), HEADERS, CORS)
                        .validate(false))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsGeneratedKeysWithoutConfiguredPem() {
        assertThatThrownBy(
                        () -> validator(jwt(null, true, false), admin(false, "Segura123456"), mfa(true), HEADERS, CORS)
                                .validate(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PRIVATE_KEY");
    }

    @Test
    void rejectsTheInsecureJwksFlag() {
        assertThatThrownBy(
                        () -> validator(jwt("pem", false, true), admin(false, "Segura123456"), mfa(true), HEADERS, CORS)
                                .validate(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_ALLOW_INSECURE_JWKS");
    }

    @Test
    void rejectsATokenTtlLongerThanTwelveHours() {
        assertThatThrownBy(() -> validator(
                                jwtWithTtl(Duration.ofHours(13)),
                                admin(false, "Segura123456"),
                                mfa(true),
                                HEADERS,
                                CORS)
                        .validate(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_TTL");
    }

    @Test
    void rejectsTheDefaultBootstrapPassword() {
        assertThatThrownBy(
                        () -> validator(jwt("pem", false, false), admin(true, "ChangeMe123!"), mfa(true), HEADERS, CORS)
                                .validate(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD");
    }

    @Test
    void acceptsTheDefaultPasswordWhenBootstrapIsDisabled() {
        assertThatCode(() -> validator(jwt("pem", false, false), admin(false, "ChangeMe123!"), mfa(true), HEADERS, CORS)
                        .validate(false))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsOptionalMfaForAdminsOutsideDevelopment() {
        assertThatThrownBy(() -> validator(
                                jwt("pem", false, false), admin(false, "Segura123456"), mfa(false), HEADERS, CORS)
                        .validate(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("multifactor");
    }

    @Test
    void rejectsAMissingMfaSecretKeyOutsideDevelopment() {
        MfaProperties withoutSecret = new MfaProperties(true, Duration.ofMinutes(5), "SM-Xplore", null);
        assertThatThrownBy(() -> validator(
                                jwt("pem", false, false), admin(false, "Segura123456"), withoutSecret, HEADERS, CORS)
                        .validate(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MFA_SECRET_KEY");
    }

    @Test
    void rejectsDisabledHstsOutsideDevelopment() {
        assertThatThrownBy(() -> validator(
                                jwt("pem", false, false),
                                admin(false, "Segura123456"),
                                mfa(true),
                                new SecurityHeadersProperties(false, 365),
                                CORS)
                        .validate(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HSTS");
    }

    @Test
    void rejectsWildcardCorsOrigins() {
        assertThatThrownBy(() -> validator(
                                jwt("pem", false, false),
                                admin(false, "Segura123456"),
                                mfa(true),
                                HEADERS,
                                new CorsProperties(List.of("*")))
                        .validate(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS");
    }

    @Test
    void rejectsLocalhostCorsOrigins() {
        assertThatThrownBy(() -> validator(
                                jwt("pem", false, false),
                                admin(false, "Segura123456"),
                                mfa(true),
                                HEADERS,
                                new CorsProperties(List.of("http://localhost:5173")))
                        .validate(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS");
    }

    @Test
    void rejectsAMissingInternalApiKey() {
        InternalApiProperties unconfigured = new InternalApiProperties(" ", List.of(), Duration.ofMinutes(5), 60);
        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                jwt("pem", false, false), admin(false, "Segura123456"), CORS, mfa(true), HEADERS, unconfigured);

        assertThatThrownBy(() -> validator.validate(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTERNAL_API_KEY");
    }

    @Test
    void onlyWarnsWhenTheProfileIsDevelopmentLike() {
        ProductionSecurityValidator insecure = validator(
                jwt(null, true, true),
                admin(true, "ChangeMe123!"),
                mfa(false),
                new SecurityHeadersProperties(false, 365),
                new CorsProperties(List.of("*")));

        assertThatCode(() -> insecure.validate(true)).doesNotThrowAnyException();
    }

    private static ProductionSecurityValidator validator(
            JwtProperties jwt,
            BootstrapAdminProperties admin,
            MfaProperties mfa,
            SecurityHeadersProperties headers,
            CorsProperties cors) {
        return new ProductionSecurityValidator(jwt, admin, cors, mfa, headers, INTERNAL);
    }

    private static JwtProperties jwt(String privateKey, boolean allowGenerated, boolean allowInsecureJwks) {
        return jwtWithTtl(Duration.ofHours(1), privateKey, allowGenerated, allowInsecureJwks);
    }

    private static JwtProperties jwtWithTtl(Duration ttl) {
        return jwtWithTtl(ttl, "pem", false, false);
    }

    private static JwtProperties jwtWithTtl(
            Duration ttl, String privateKey, boolean allowGenerated, boolean allowInsecureJwks) {
        return new JwtProperties("i", "a", ttl, privateKey, privateKey, null, allowGenerated, allowInsecureJwks);
    }

    private static BootstrapAdminProperties admin(boolean enabled, String password) {
        return new BootstrapAdminProperties(enabled, "admin@smxplore.com", password, "Admin");
    }

    private static MfaProperties mfa(boolean requiredForAdmins) {
        return new MfaProperties(requiredForAdmins, Duration.ofMinutes(5), "SM-Xplore", "clave-mfa");
    }
}
