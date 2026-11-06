package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.port.in.GetUserContactUseCase;
import com.smxplore.users.infrastructure.config.InternalApiProperties;
import com.smxplore.users.infrastructure.security.InternalApiKeyVerifier;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Endpoint interno para que otro microservicio resuelva los datos de contacto de una cuenta sin depender
 * de un token de usuario (P2-9).
 *
 * <p>La ruta se autentica con el encabezado {@value #API_KEY_HEADER}, comparado en tiempo constante contra
 * {@code app.internal.api-key} ({@code INTERNAL_API_KEY}). No usa JWT porque el llamador es un servicio y
 * no una persona. El gateway bloquea {@code /internal/**} hacia el exterior, de modo que esta ruta no es
 * alcanzable desde Internet; cuando se firme P3-8, la clave compartida se sustituye por autenticación
 * servicio a servicio.
 *
 * <p>Si la clave no está configurada responde {@code 503} para no quedar abierto por accidente; si no
 * coincide, {@code 403}. La clave se valida antes de buscar la cuenta, así que un llamador no autorizado no
 * puede inferir qué identificadores existen.
 */
@RestController
@RequestMapping("/internal")
public class InternalContactController {

    /** Encabezado compartido entre los servicios del proyecto. */
    public static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final GetUserContactUseCase getUserContact;
    private final InternalApiProperties properties;

    public InternalContactController(GetUserContactUseCase getUserContact, InternalApiProperties properties) {
        this.getUserContact = getUserContact;
        this.properties = properties;
    }

    @GetMapping("/users/{userId}/contact")
    public Mono<ResponseEntity<Object>> contact(
            @PathVariable UUID userId, @RequestHeader(name = API_KEY_HEADER, required = false) String apiKey) {
        if (!properties.configured()) {
            return Mono.just(problem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "INTERNAL_API_NOT_CONFIGURED",
                    "El endpoint interno no está habilitado: falta configurar INTERNAL_API_KEY"));
        }
        if (!InternalApiKeyVerifier.matches(properties.apiKey(), apiKey)) {
            return Mono.just(
                    problem(HttpStatus.FORBIDDEN, "INVALID_INTERNAL_API_KEY", "La clave interna no es válida"));
        }
        return getUserContact.get(userId).map(view -> ResponseEntity.ok().body(view));
    }

    private static ResponseEntity<Object> problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }
}
