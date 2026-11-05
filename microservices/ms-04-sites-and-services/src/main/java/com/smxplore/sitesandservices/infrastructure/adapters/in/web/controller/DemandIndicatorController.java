package com.smxplore.sitesandservices.infrastructure.adapters.in.web.controller;

import com.smxplore.sitesandservices.application.indicator.DemandCsvFormatter;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.indicator.DemandFilter;
import com.smxplore.sitesandservices.domain.model.indicator.DemandGranularity;
import com.smxplore.sitesandservices.domain.model.indicator.DemandIndicators;
import com.smxplore.sitesandservices.domain.ports.in.GetDemandIndicatorsUseCase;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.indicator.DemandIndicatorsResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.security.JwtUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/demand-indicators")
@Tag(name = "Demand indicators",
        description = "Role-scoped demand, occupancy and seasonality indicators for services and sites.")
public class DemandIndicatorController {
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final GetDemandIndicatorsUseCase getDemandIndicators;
    private final JwtUserResolver jwtUserResolver;
    private final Clock clock;

    @GetMapping
    @Operation(summary = "Get demand indicators for the current provider or destination manager")
    public Mono<DemandIndicatorsResponse> indicators(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(defaultValue = "DAY") DemandGranularity granularity,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return getDemandIndicators.handle(filter(from, to, zoneId, siteId, granularity), actor)
                .map(DemandIndicatorsResponse::from);
    }

    @GetMapping("/report")
    @Operation(summary = "Download the demand indicators of the period as CSV")
    public Mono<ResponseEntity<String>> report(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(defaultValue = "DAY") DemandGranularity granularity,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return getDemandIndicators.handle(filter(from, to, zoneId, siteId, granularity), actor)
                .map(this::csv);
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
        return new DemandFilter(
                effectiveFrom,
                effectiveTo,
                zoneId,
                siteId,
                granularity == null ? DemandGranularity.DAY : granularity);
    }

    private ResponseEntity<String> csv(DemandIndicators indicators) {
        String filename = "indicadores-demanda-"
                + indicators.from().toString().replace(":", "-")
                + "-"
                + indicators.to().toString().replace(":", "-")
                + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(DemandCsvFormatter.format(indicators));
    }
}
