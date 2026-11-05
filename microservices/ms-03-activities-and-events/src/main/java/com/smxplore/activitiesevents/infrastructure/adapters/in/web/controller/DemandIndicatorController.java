package com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller;

import com.smxplore.activitiesevents.application.indicator.DemandCsvFormatter;
import com.smxplore.activitiesevents.application.indicator.DemandPdfFormatter;
import com.smxplore.activitiesevents.domain.model.indicator.DemandFilter;
import com.smxplore.activitiesevents.domain.model.indicator.DemandGranularity;
import com.smxplore.activitiesevents.domain.model.indicator.DemandIndicators;
import com.smxplore.activitiesevents.domain.model.user.UserRole;
import com.smxplore.activitiesevents.domain.ports.usecases.indicator.GetDemandIndicatorsUseCasePort;
import com.smxplore.activitiesevents.infrastructure.config.JwtProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/demand-indicators")
@Tag(name = "Demand indicators",
        description = "Role-scoped demand, occupancy and seasonality indicators for activities, events "
                + "and the destination's services.")
public class DemandIndicatorController {

    private static final int DEFAULT_RANGE_DAYS = 30;

    private final GetDemandIndicatorsUseCasePort getDemandIndicators;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    @GetMapping
    @Operation(summary = "Get demand indicators for the current guide, destination manager or admin")
    public Mono<DemandIndicators> indicators(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(defaultValue = "DAY") DemandGranularity granularity,
            @AuthenticationPrincipal Jwt jwt) {
        return getDemandIndicators.handle(filter(from, to, zoneId, siteId, granularity),
                        subject(jwt), role(jwt), jwt.getTokenValue());
    }

    @GetMapping("/report")
    @Operation(summary = "Download the demand indicators of the period as CSV")
    public Mono<ResponseEntity<String>> report(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(defaultValue = "DAY") DemandGranularity granularity,
            @AuthenticationPrincipal Jwt jwt) {
        return getDemandIndicators.handle(filter(from, to, zoneId, siteId, granularity),
                        subject(jwt), role(jwt), jwt.getTokenValue())
                .map(this::csv);
    }

    @GetMapping("/report.pdf")
    @Operation(summary = "Download the demand indicators of the period as PDF")
    public Mono<ResponseEntity<byte[]>> reportPdf(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(defaultValue = "DAY") DemandGranularity granularity,
            @AuthenticationPrincipal Jwt jwt) {
        return getDemandIndicators.handle(filter(from, to, zoneId, siteId, granularity),
                        subject(jwt), role(jwt), jwt.getTokenValue())
                .map(this::pdf);
    }

    private DemandFilter filter(
            Instant from,
            Instant to,
            UUID zoneId,
            UUID siteId,
            DemandGranularity granularity
    ) {
        Instant effectiveTo = to == null ? Instant.now(clock) : to;
        Instant effectiveFrom = from == null
                ? effectiveTo.minus(Duration.ofDays(DEFAULT_RANGE_DAYS))
                : from;
        return new DemandFilter(effectiveFrom, effectiveTo, zoneId, siteId,
                granularity == null ? DemandGranularity.DAY : granularity);
    }

    private ResponseEntity<String> csv(DemandIndicators indicators) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename(indicators, "csv")).build().toString())
                .body(DemandCsvFormatter.format(indicators));
    }

    private ResponseEntity<byte[]> pdf(DemandIndicators indicators) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename(indicators, "pdf")).build().toString())
                .body(DemandPdfFormatter.format(indicators));
    }

    private static String filename(DemandIndicators indicators, String extension) {
        return "indicadores-demanda-"
                + indicators.from().toString().replace(":", "-")
                + "-"
                + indicators.to().toString().replace(":", "-")
                + "." + extension;
    }

    private UserRole role(Jwt jwt) {
        return jwt == null ? null : UserRole.fromClaim(jwt.getClaimAsString(jwtProperties.getRoleClaim()));
    }

    private static UUID subject(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token subject is required.");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject.");
        }
    }
}
