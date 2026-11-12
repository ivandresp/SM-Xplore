package com.smxplore.users.infrastructure.security;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.infrastructure.config.CorsProperties;
import com.smxplore.users.infrastructure.config.MfaProperties;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AsyncCache<UUID, Long> authVersionCache() {
        // MS01-15: el TTL baja de 60 s a 15 s. Además cada operación que incrementa authVersion descarta la entrada
        // del usuario en AuthVersionEvictor, así que este TTL solo cubre procesos que no pueden evictar (réplicas).
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(15))
                .maximumSize(10_000)
                .buildAsync();
    }

    @Bean
    public AuthVersionFilter authVersionFilter(UserRepository users, AsyncCache<UUID, Long> authVersionCache) {
        return new AuthVersionFilter(users, authVersionCache);
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtAuthenticationConverter jwtAuthenticationConverter,
            ServerAuthenticationEntryPoint authenticationEntryPoint,
            ServerAccessDeniedHandler accessDeniedHandler,
            AuthVersionFilter authVersionFilter,
            MfaProperties mfaProperties,
            @Value("${app.security.actuator.public-metrics:false}") boolean publicMetrics) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeExchange(exchanges -> {
                    // MS01-14: solo health, info y JWKS quedan públicos. Las métricas y Prometheus no se exponen
                    // por defecto: requieren JWT salvo que ACTUATOR_PUBLIC_METRICS=true (uso local/dev con
                    // `management.endpoints.web.exposure.include` ampliado de forma explícita).
                    exchanges
                            .pathMatchers(
                                    "/api/v1/auth/**",
                                    "/api/v1/public/**",
                                    "/actuator/health",
                                    "/actuator/health/**",
                                    "/actuator/info",
                                    "/v3/api-docs/**",
                                    "/swagger-ui/**",
                                    "/swagger-ui.html",
                                    "/.well-known/jwks.json")
                            .permitAll();
                    if (publicMetrics) {
                        exchanges
                                .pathMatchers("/actuator/metrics", "/actuator/metrics/**", "/actuator/prometheus")
                                .permitAll();
                    }
                    exchanges
                            // Endpoint interno de contactos (P2-9): lo consume otro microservicio, no una persona,
                            // así que no se exige un JWT de usuario. La autenticación real se hace en el controlador
                            // con firma HMAC por servicio (X-Internal-Service, X-Internal-Timestamp y
                            // X-Internal-Signature); si la clave maestra no está configurada responde 503. Solo se
                            // abre esta ruta exacta: el resto de /internal/** cae en anyExchange().authenticated().
                            // El gateway bloquea /internal/** desde el exterior.
                            .pathMatchers(HttpMethod.GET, "/internal/users/*/contact")
                            .permitAll()
                            .pathMatchers("/api/v1/admin/**")
                            .hasRole("ADMIN")
                            .pathMatchers(HttpMethod.OPTIONS, "/**")
                            .permitAll()
                            .anyExchange()
                            .authenticated();
                })
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .addFilterAfter(authVersionFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(new MfaEnrollmentRequiredFilter(mfaProperties), SecurityWebFiltersOrder.AUTHORIZATION)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
