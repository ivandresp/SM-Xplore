package com.smxplore.sitesandservices.infrastructure.config;

import com.smxplore.sitesandservices.domain.exception.InvalidJwtException;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.error.SecurityProblemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            ReactiveJwtAuthenticationConverter jwtAuthenticationConverter,
            ServerAuthenticationEntryPoint apiAuthenticationEntryPoint,
            ServerAccessDeniedHandler apiAccessDeniedHandler
    ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/metrics",
                                "/actuator/metrics/**",
                                "/actuator/prometheus"
                        ).permitAll()
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                        .pathMatchers(HttpMethod.POST, "/api/v1/categories", "/api/v1/zones", "/api/v1/sites")
                        .hasAnyRole("GESTORA_DESTINOS", "ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/categories/**", "/api/v1/zones/**", "/api/v1/sites/**")
                        .hasAnyRole("GESTORA_DESTINOS", "ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/zones/**", "/api/v1/sites/**")
                        .hasAnyRole("GESTORA_DESTINOS", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/v1/services")
                        .hasAnyRole("PRESTADORA_SERVICIOS", "ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/services/**")
                        .hasAnyRole("PRESTADORA_SERVICIOS", "ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/services/**")
                        .hasAnyRole("PRESTADORA_SERVICIOS", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/v1/reviews")
                        .hasRole("TURISTA")
                        .pathMatchers(HttpMethod.POST, "/api/v1/reviews/*/response")
                        .hasAnyRole("PRESTADORA_SERVICIOS", "GESTORA_DESTINOS", "ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/reviews/*/status")
                        .hasRole("ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/v1/reservations")
                        .hasRole("TURISTA")
                        .pathMatchers(HttpMethod.POST, "/api/v1/admin/outbox/reprocess")
                        .hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/reservations/*/status")
                        .hasAnyRole("TURISTA", "PRESTADORA_SERVICIOS", "ADMIN")
                        .anyExchange().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter(JwtProperties properties) {
        var converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString(properties.getRoleClaim());
            if (role == null || role.isBlank()) {
                return Flux.empty();
            }
            try {
                return Flux.just(new SimpleGrantedAuthority("ROLE_" + UserRole.fromClaim(role).name()));
            } catch (InvalidJwtException exception) {
                return Flux.empty();
            }
        });
        return converter;
    }

    @Bean
    public ServerAuthenticationEntryPoint apiAuthenticationEntryPoint(SecurityProblemWriter problemWriter) {
        return (exchange, exception) -> problemWriter.write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "Authentication is required or the token is invalid."
        );
    }

    @Bean
    public ServerAccessDeniedHandler apiAccessDeniedHandler(SecurityProblemWriter problemWriter) {
        return (exchange, exception) -> problemWriter.write(
                exchange,
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "Your role is not allowed to perform this operation."
        );
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.stream(properties.getAllowedOrigins().split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Correlation-Id",
                "Idempotency-Key"
        ));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
