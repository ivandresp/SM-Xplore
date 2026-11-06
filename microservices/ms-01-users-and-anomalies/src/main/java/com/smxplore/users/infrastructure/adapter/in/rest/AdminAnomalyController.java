package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.dto.AnomalySearchCriteria;
import com.smxplore.users.application.dto.AnomalyView;
import com.smxplore.users.application.dto.ClassifyAnomalyCommand;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.ResolveAnomalyCommand;
import com.smxplore.users.application.port.in.ClassifyAnomalyUseCase;
import com.smxplore.users.application.port.in.DetectAnomaliesUseCase;
import com.smxplore.users.application.port.in.GetAnomalyUseCase;
import com.smxplore.users.application.port.in.ListAnomaliesUseCase;
import com.smxplore.users.application.port.in.ResolveAnomalyUseCase;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.AnomalySeverity;
import com.smxplore.users.domain.model.AnomalyStatus;
import com.smxplore.users.domain.model.AnomalyType;
import com.smxplore.users.infrastructure.adapter.in.dto.ClassifyAnomalyRequest;
import com.smxplore.users.infrastructure.adapter.in.dto.ResolveAnomalyRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/admin/anomalies")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnomalyController {

    private final ListAnomaliesUseCase listAnomalies;
    private final GetAnomalyUseCase getAnomaly;
    private final ClassifyAnomalyUseCase classifyAnomaly;
    private final ResolveAnomalyUseCase resolveAnomaly;
    private final DetectAnomaliesUseCase detectAnomalies;

    public AdminAnomalyController(
            ListAnomaliesUseCase listAnomalies,
            GetAnomalyUseCase getAnomaly,
            ClassifyAnomalyUseCase classifyAnomaly,
            ResolveAnomalyUseCase resolveAnomaly,
            DetectAnomaliesUseCase detectAnomalies) {
        this.listAnomalies = listAnomalies;
        this.getAnomaly = getAnomaly;
        this.classifyAnomaly = classifyAnomaly;
        this.resolveAnomaly = resolveAnomaly;
        this.detectAnomalies = detectAnomalies;
    }

    @GetMapping
    public Mono<PageResult<AnomalyView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return listAnomalies.list(new AnomalySearchCriteria(
                parseStatus(status), parseSeverity(severity), parseType(type), from, to, page, size));
    }

    @GetMapping("/{id}")
    public Mono<AnomalyView> get(@PathVariable UUID id) {
        return getAnomaly.get(id);
    }

    @PatchMapping("/{id}/severity")
    public Mono<AnomalyView> classify(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ClassifyAnomalyRequest request) {
        return classifyAnomaly.classify(
                actorId(jwt), id, new ClassifyAnomalyCommand(parseSeverityRequired(request.severity())));
    }

    @PatchMapping("/{id}/resolve")
    public Mono<AnomalyView> resolve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ResolveAnomalyRequest request) {
        return resolveAnomaly.resolve(actorId(jwt), id, new ResolveAnomalyCommand(request.note()));
    }

    @PostMapping("/detect")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> detect() {
        return detectAnomalies.detect().then();
    }

    private static UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static AnomalyStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AnomalyStatus.fromCode(value);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("El estado de la anomalía no es válido: " + value);
        }
    }

    private static AnomalySeverity parseSeverity(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AnomalySeverity.fromCode(value);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("La severidad de la anomalía no es válida: " + value);
        }
    }

    private static AnomalySeverity parseSeverityRequired(String value) {
        AnomalySeverity severity = parseSeverity(value);
        if (severity == null) {
            throw new ValidationException("La severidad de la anomalía es obligatoria");
        }
        return severity;
    }

    private static AnomalyType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AnomalyType.fromCode(value);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("El tipo de anomalía no es válido: " + value);
        }
    }
}
