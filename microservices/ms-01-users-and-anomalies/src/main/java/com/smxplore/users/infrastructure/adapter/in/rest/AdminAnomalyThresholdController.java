package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.dto.AnomalyThresholdView;
import com.smxplore.users.application.dto.UpdateAnomalyThresholdCommand;
import com.smxplore.users.application.port.in.ListAnomalyThresholdsUseCase;
import com.smxplore.users.application.port.in.UpdateAnomalyThresholdUseCase;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.AnomalySeverity;
import com.smxplore.users.domain.model.AnomalyType;
import com.smxplore.users.infrastructure.adapter.in.dto.UpdateAnomalyThresholdRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/admin/anomaly-thresholds")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnomalyThresholdController {

    private final ListAnomalyThresholdsUseCase listThresholds;
    private final UpdateAnomalyThresholdUseCase updateThreshold;

    public AdminAnomalyThresholdController(
            ListAnomalyThresholdsUseCase listThresholds, UpdateAnomalyThresholdUseCase updateThreshold) {
        this.listThresholds = listThresholds;
        this.updateThreshold = updateThreshold;
    }

    @GetMapping
    public Flux<AnomalyThresholdView> list() {
        return listThresholds.list();
    }

    @PutMapping("/{type}")
    public Mono<AnomalyThresholdView> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String type,
            @Valid @RequestBody UpdateAnomalyThresholdRequest request) {
        return updateThreshold.update(
                actorId(jwt),
                parseType(type),
                new UpdateAnomalyThresholdCommand(
                        request.thresholdValue(),
                        request.windowSeconds(),
                        parseSeverity(request.severity()),
                        request.enabled()));
    }

    private static UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static AnomalyType parseType(String value) {
        try {
            return AnomalyType.fromCode(value);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("El tipo de anomalía no es válido: " + value);
        }
    }

    private static AnomalySeverity parseSeverity(String value) {
        try {
            return AnomalySeverity.fromCode(value);
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("La severidad de la anomalía no es válida: " + value);
        }
    }
}
