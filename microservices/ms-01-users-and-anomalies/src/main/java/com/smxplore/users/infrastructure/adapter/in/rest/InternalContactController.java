package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.port.in.GetUserContactUseCase;
import com.smxplore.users.application.port.out.InternalAccessLog;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.infrastructure.config.InternalApiProperties;
import com.smxplore.users.infrastructure.security.InternalRequestAuthenticator;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Endpoint interno para que otros microservicios consulten los datos de contacto de un usuario.
 *
 * <p>MS01-11: la cabecera compartida {@code X-Internal-Api-Key} se sustituyó por una firma HMAC por servicio. Cada
 * petición debe traer {@code X-Internal-Service}, {@code X-Internal-Timestamp} (segundos epoch) y
 * {@code X-Internal-Signature} (HMAC-SHA256 en hex de {@code servicio\nmétodo\nruta\ntimestamp}, con la clave derivada
 * del secreto maestro). Así el emisor queda identificado y cada acceso a datos personales queda auditado en
 * {@code internal_access_log}; la lista blanca {@code app.internal.allowed-services} limita qué servicios pueden llamar.
 *
 * <p>No usa JWT porque el llamador es otro servicio, no un usuario. La red del gateway bloquea {@code /internal/**}
 * desde fuera.
 */
@RestController
@RequestMapping("/internal")
public class InternalContactController {

    public static final String SERVICE_HEADER = "X-Internal-Service";

    public static final String TIMESTAMP_HEADER = "X-Internal-Timestamp";

    public static final String SIGNATURE_HEADER = "X-Internal-Signature";

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalContactController.class);

    private final GetUserContactUseCase getUserContact;

    private final InternalApiProperties properties;

    private final InternalRequestAuthenticator authenticator;

    private final InternalAccessLog accessLog;

    private final Clock clock;

    public InternalContactController(
            GetUserContactUseCase getUserContact,
            InternalApiProperties properties,
            InternalRequestAuthenticator authenticator,
            InternalAccessLog accessLog,
            Clock clock) {
        this.getUserContact = getUserContact;
        this.properties = properties;
        this.authenticator = authenticator;
        this.accessLog = accessLog;
        this.clock = clock;
    }

    @GetMapping("/users/{userId}/contact")
    public Mono<ResponseEntity<Object>> contact(
            @PathVariable UUID userId,
            @RequestHeader(name = SERVICE_HEADER, required = false) String service,
            @RequestHeader(name = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature,
            ServerWebExchange exchange) {
        if (!properties.configured()) {
            return Mono.just(problem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "INTERNAL_API_NOT_CONFIGURED",
                    "El endpoint interno no está habilitado: falta configurar INTERNAL_API_KEY"));
        }
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        InternalRequestAuthenticator.Outcome outcome =
                authenticator.authenticate(service, timestamp, signature, method, path);
        if (outcome == InternalRequestAuthenticator.Outcome.INVALID_SERVICE) {
            return audited(
                    audit(service, userId, "INVALID_SERVICE"),
                    problem(
                            HttpStatus.FORBIDDEN,
                            "INVALID_INTERNAL_SERVICE",
                            "El servicio interno no está autorizado"));
        }
        if (outcome == InternalRequestAuthenticator.Outcome.INVALID_SIGNATURE) {
            return audited(
                    audit(service, userId, "INVALID_SIGNATURE"),
                    problem(HttpStatus.FORBIDDEN, "INVALID_INTERNAL_SIGNATURE", "La firma interna no es válida"));
        }
        if (!authenticator.tryAcquire(service)) {
            return audited(
                    audit(service, userId, "RATE_LIMITED"),
                    problem(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "RATE_LIMIT_EXCEEDED",
                            "Demasiadas solicitudes internas; espera antes de volver a intentarlo"));
        }
        return getUserContact
                .get(userId)
                .flatMap(view -> audited(
                        audit(service, userId, "OK"), ResponseEntity.ok().<Object>body(view)))
                .onErrorResume(
                        UserNotFoundException.class,
                        exception -> audited(
                                audit(service, userId, "NOT_FOUND"),
                                problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "El usuario no existe")));
    }

    private Mono<Void> audit(String service, UUID userId, String outcome) {
        String serviceName = service == null || service.isBlank() ? "desconocido" : service;
        return accessLog.record(serviceName, userId, outcome, clock.instant()).onErrorResume(exception -> {
            // La auditoría no puede tumbar la disponibilidad: se registra el fallo y se continúa.
            LOGGER.error("No se pudo registrar el acceso interno a {} ({})", userId, outcome, exception);
            return Mono.empty();
        });
    }

    private static <T> Mono<T> audited(Mono<Void> audit, T response) {
        return audit.thenReturn(response);
    }

    private static ResponseEntity<Object> problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }
}
