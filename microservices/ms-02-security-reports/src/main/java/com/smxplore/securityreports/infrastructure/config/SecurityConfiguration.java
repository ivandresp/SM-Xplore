package com.smxplore.securityreports.infrastructure.config;

import com.smxplore.securityreports.domain.model.types.UserRole;
import com.smxplore.securityreports.infrastructure.adapters.in.web.error.ApiErrorResponse;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

/**
 * Single security chain for every profile: stateless JWT authentication with
 * role-based authorization. The endpoint-to-role matrix below follows the
 * use-case diagrams (destination managers manage the offer, guides manage
 * activities, providers manage services, tourists operate on their own
 * reservations, admins administer users and see everything).
 *
 * <p>Note: this chain enforces roles; resource ownership (owner/provider/guide/
 * staff scoping) is enforced in the web controllers. See the README
 * ("Security notes") for the details.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

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
                        // Public: CORS preflight, authentication, API docs and health
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**")
                        .permitAll()
                        // Security reports: everyone reports/comments; listing all and closing is admin/manager
                        .pathMatchers(HttpMethod.GET, "/api/v1/security-reports")
                        .hasAnyRole(UserRole.ADMIN.name(), UserRole.DESTINATION_MANAGER.name())
                        .pathMatchers(HttpMethod.HEAD, "/api/v1/security-reports")
                        .hasAnyRole(UserRole.ADMIN.name(), UserRole.DESTINATION_MANAGER.name())
                        .pathMatchers(HttpMethod.POST, "/api/v1/security-reports/*/close",
                                "/api/v1/security-reports/*/solve")
                        .hasAnyRole(UserRole.ADMIN.name(), UserRole.DESTINATION_MANAGER.name())
                        // Everything else: a valid JWT is enough
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
    public ServerAuthenticationEntryPoint apiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return (exchange, exception) -> {
            exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "Authentication required or token invalid.",
                    objectMapper);
        };
    }

    @Bean
    public ServerAccessDeniedHandler apiAccessDeniedHandler(ObjectMapper objectMapper) {
        return (exchange, exception) -> writeError(exchange, HttpStatus.FORBIDDEN,
                "Insufficient role for this operation.", objectMapper);
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

    private static Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message,
                                         ObjectMapper objectMapper) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            var json = objectMapper.writeValueAsBytes(ApiErrorResponse.of(status, message, exchange));
            var buffer = response.bufferFactory().wrap(json);
            return response.writeWith(Mono.just(buffer));
        } catch (JacksonException exception) {
            return Mono.error(exception);
        }
    }
}