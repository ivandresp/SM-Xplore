package com.smxplore.recommendations.infrastructure.config;

import com.smxplore.recommendations.infrastructure.adapters.in.web.error.SecurityProblemWriter;
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
                        .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/recommendations").hasAnyRole("TOURIST", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/v1/preferences").hasRole("TOURIST")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/preferences/**").hasRole("TOURIST")
                        .pathMatchers(HttpMethod.POST, "/api/v1/interactions/view/**").hasRole("TOURIST")
                        .pathMatchers("/internal/admin/**").hasRole("ADMIN")
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
            return role == null || role.isBlank()
                    ? Flux.empty()
                    : Flux.just(new SimpleGrantedAuthority("ROLE_" + role));
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
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
