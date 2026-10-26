package com.smxplore.activitiesevents.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Single stateless JWT security chain. Catalog reads are public; writes are
 * restricted to the roles that own the offer (destination managers for events,
 * guides and managers for activities).
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    private static final String ADMIN = "ADMIN";
    private static final String DESTINATION_MANAGER = "DESTINATION_MANAGER";
    private static final String GUIDE = "GUIDE";

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         CorsConfigurationSource corsConfigurationSource,
                                                         ReactiveJwtAuthenticationConverter jwtAuthenticationConverter,
                                                         ServerAuthenticationEntryPoint apiAuthenticationEntryPoint,
                                                         ServerAccessDeniedHandler apiAccessDeniedHandler) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**")
                        .permitAll()
                        // Public catalog
                        .pathMatchers(HttpMethod.GET, "/api/v1/events/**", "/api/v1/activities/**").permitAll()
                        .pathMatchers(HttpMethod.HEAD, "/api/v1/events/**", "/api/v1/activities/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/reviews/**").permitAll()
                        // Events: destination managers (and admins) manage the offer
                        .pathMatchers(HttpMethod.POST, "/api/v1/events/**")
                        .hasAnyRole(ADMIN, DESTINATION_MANAGER)
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/events/**")
                        .hasAnyRole(ADMIN, DESTINATION_MANAGER)
                        // Activities: guides own their activities; managers and admins oversee
                        .pathMatchers(HttpMethod.POST, "/api/v1/activities/**")
                        .hasAnyRole(ADMIN, DESTINATION_MANAGER, GUIDE)
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/activities/**")
                        .hasAnyRole(ADMIN, DESTINATION_MANAGER, GUIDE)
                        // Reservations: staff-only global listings; ownership is enforced in the controller
                        .pathMatchers(HttpMethod.GET, "/api/v1/reservations")
                        .hasAnyRole(ADMIN, DESTINATION_MANAGER)
                        .pathMatchers("/api/v1/review-reports/**")
                        .hasAnyRole(ADMIN, DESTINATION_MANAGER)
                        .pathMatchers("/api/v1/reservations/**").authenticated()
                        .anyExchange().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter(JwtProperties jwtProperties) {
        var converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var role = jwt.getClaimAsString(jwtProperties.getRoleClaim());
            return role == null
                    ? Flux.empty()
                    : Flux.just(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    @Bean
    public ServerAuthenticationEntryPoint apiAuthenticationEntryPoint() {
        return (exchange, exception) -> {
            exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "Authentication required or token invalid.");
        };
    }

    @Bean
    public ServerAccessDeniedHandler apiAccessDeniedHandler() {
        return (exchange, exception) -> writeError(exchange, HttpStatus.FORBIDDEN,
                "Insufficient role for this operation.");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.cors.allowed-origins}") String allowedOrigins) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var json = ("{\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase()
                + "\",\"message\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(json)));
    }
}
