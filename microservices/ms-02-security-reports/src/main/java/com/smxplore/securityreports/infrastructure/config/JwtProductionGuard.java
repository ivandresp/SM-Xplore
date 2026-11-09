package com.smxplore.securityreports.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Falla al arrancar si la configuracion de seguridad queda permisiva sin un perfil de desarrollo explicito.
 *
 * <p>MS02-05: antes el endurecimiento (HTTPS del JWKS, validacion {@code ver}, MFA de administradores,
 * springdoc apagado) solo se exigia con el perfil {@code prod}; un contenedor arrancado sin
 * {@code SPRING_PROFILES_ACTIVE}, o con un perfil intermedio, quedaba sin esos controles sin que nadie lo notara.
 * Ahora el comportamiento por defecto es estricto y solo {@code local}, {@code dev} y {@code test} relajan los
 * valores, de forma explicita y avisando en el log.
 */
@Slf4j
@Component
public class JwtProductionGuard implements InitializingBean {

    private static final String HTTP_SCHEME_PREFIX = "http://";
    private static final Profiles RELAXED_PROFILES = Profiles.of("local", "dev", "test");

    private final JwtProperties properties;
    private final AuthVersionProperties authVersionProperties;
    private final PhotoUrlProperties photoUrlProperties;
    private final Environment environment;
    private final boolean requireAdminMfa;

    public JwtProductionGuard(JwtProperties properties,
                              AuthVersionProperties authVersionProperties,
                              PhotoUrlProperties photoUrlProperties,
                              Environment environment,
                              @Value("${app.security.mfa.require-admin:true}") boolean requireAdminMfa) {
        this.properties = properties;
        this.authVersionProperties = authVersionProperties;
        this.photoUrlProperties = photoUrlProperties;
        this.environment = environment;
        this.requireAdminMfa = requireAdminMfa;
    }

    @Override
    public void afterPropertiesSet() {
        if (environment.acceptsProfiles(RELAXED_PROFILES)) {
            log.warn("Security defaults relaxed by a local/dev/test profile: JWT_ALLOW_INSECURE_JWKS, "
                    + "auth-version validation, admin MFA, springdoc and the photo allowlist are not enforced.");
            return;
        }
        require(!properties.isAllowInsecureJwks(),
                "JWT_ALLOW_INSECURE_JWKS is a development flag: outside the local, dev or test profiles it must"
                        + " be false and the JWKS must be served over HTTPS.");
        String uri = properties.getJwkSetUri();
        boolean hasJwksUri = StringUtils.hasText(uri);
        require(hasJwksUri || StringUtils.hasText(properties.getPublicKeyLocation()),
                "app.security.jwt.jwk-set-uri or app.security.jwt.public-key-location is required outside the"
                        + " local, dev or test profiles.");
        if (hasJwksUri) {
            require(!uri.regionMatches(true, 0, HTTP_SCHEME_PREFIX, 0, HTTP_SCHEME_PREFIX.length()),
                    "app.security.jwt.jwk-set-uri must use https outside the local, dev or test profiles.");
        }
        require(StringUtils.hasText(properties.getIssuer()),
                "app.security.jwt.issuer is required outside the local, dev or test profiles.");
        require(StringUtils.hasText(properties.getAudience()),
                "app.security.jwt.audience is required outside the local, dev or test profiles.");
        require(authVersionProperties.isEnabled(),
                "app.security.auth-version.enabled must be true outside the local, dev or test profiles"
                        + " (AUTH_VERSION_ENABLED=true and MS01_BASE_URL are required).");
        require(requireAdminMfa,
                "app.security.mfa.require-admin must be true outside the local, dev or test profiles"
                        + " (MFA_REQUIRE_ADMIN=true).");
        require(!Boolean.TRUE.equals(environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false)),
                "springdoc.api-docs.enabled must be false outside the local, dev or test profiles.");
        String exposedEndpoints = environment.getProperty("management.endpoints.web.exposure.include", "");
        require(exposedEndpoints == null || !(exposedEndpoints.contains("metrics")
                        || exposedEndpoints.contains("prometheus") || exposedEndpoints.contains("*")),
                "management.endpoints.web.exposure.include must not expose metrics or prometheus outside the"
                        + " local, dev or test profiles.");
        require(photoUrlProperties.isAllowAnyHost() || photoUrlProperties.hasAllowedHosts(),
                "app.security.photos.allowed-hosts must be configured outside the local, dev or test profiles.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
