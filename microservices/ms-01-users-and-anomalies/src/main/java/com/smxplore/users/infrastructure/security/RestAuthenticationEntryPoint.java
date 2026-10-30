package com.smxplore.users.infrastructure.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RestAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        return SecurityProblemWriter.write(
                exchange.getResponse(),
                HttpStatus.UNAUTHORIZED,
                "La sesión no es válida o no se envió un token",
                "UNAUTHORIZED");
    }
}
