package com.smxplore.users.infrastructure.security;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

final class SecurityProblemWriter {

    private SecurityProblemWriter() {}

    static Mono<Void> write(ServerHttpResponse response, HttpStatus status, String detail, String code) {
        String body = "{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\",\"code\":\"%s\"}"
                .formatted(status.getReasonPhrase(), status.value(), detail, code);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().setContentLength(bytes.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
