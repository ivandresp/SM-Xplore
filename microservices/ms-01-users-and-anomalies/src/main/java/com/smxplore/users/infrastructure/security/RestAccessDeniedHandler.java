package com.smxplore.users.infrastructure.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RestAccessDeniedHandler implements ServerAccessDeniedHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException exception) {
        return SecurityProblemWriter.write(
                exchange.getResponse(),
                HttpStatus.FORBIDDEN,
                "No tienes permisos para realizar esta operación",
                "ACCESS_DENIED");
    }
}
