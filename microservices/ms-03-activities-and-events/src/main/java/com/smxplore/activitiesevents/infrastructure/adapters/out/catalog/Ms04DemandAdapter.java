package com.smxplore.activitiesevents.infrastructure.adapters.out.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smxplore.activitiesevents.domain.model.indicator.DemandBucket;
import com.smxplore.activitiesevents.domain.model.indicator.DemandComparison;
import com.smxplore.activitiesevents.domain.model.indicator.DemandDistribution;
import com.smxplore.activitiesevents.domain.model.indicator.DemandFilter;
import com.smxplore.activitiesevents.domain.model.indicator.DemandSummary;
import com.smxplore.activitiesevents.domain.model.indicator.ServiceDemand;
import com.smxplore.activitiesevents.domain.model.indicator.SiteDemand;
import com.smxplore.activitiesevents.domain.model.indicator.ZoneDemand;
import com.smxplore.activitiesevents.domain.ports.repository.Ms04DemandPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Calls the ms-04 demand indicator endpoint so the destination manager gets a single view that
 * includes services. The caller's bearer token is forwarded: ms-04 applies its own role scope.
 */
@Component
public class Ms04DemandAdapter implements Ms04DemandPort {

    private final WebClient webClient;
    private final Duration timeout;

    public Ms04DemandAdapter(@Value("${app.ms04.base-url}") String baseUrl,
                             @Value("${app.ms04.timeout}") Duration timeout) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.timeout = timeout;
    }

    @Override
    public Mono<ServicesDemand> fetch(DemandFilter filter, String bearerToken) {
        return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/v1/demand-indicators")
                            .queryParam("from", filter.from())
                            .queryParam("to", filter.to())
                            .queryParam("granularity", filter.granularity());
                    if (filter.zoneId() != null) {
                        builder.queryParam("zoneId", filter.zoneId());
                    }
                    if (filter.siteId() != null) {
                        builder.queryParam("siteId", filter.siteId());
                    }
                    return builder.build();
                })
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .retrieve()
                .bodyToMono(Ms04Response.class)
                .timeout(timeout)
                .map(response -> {
                    var summary = response.summary() == null ? DemandSummary.empty() : response.summary();
                    var comparison = response.comparison() == null
                            ? new DemandComparison(summary, DemandSummary.empty(), 0, 0, 0, 0)
                            : response.comparison();
                    return new ServicesDemand(
                            summary,
                            comparison,
                            response.timeline(),
                            response.weekdays(),
                            response.hours(),
                            response.zones(),
                            response.sites(),
                            response.services());
                });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Ms04Response(
            DemandSummary summary,
            DemandComparison comparison,
            List<DemandBucket> timeline,
            List<DemandDistribution> weekdays,
            List<DemandDistribution> hours,
            List<ZoneDemand> zones,
            List<SiteDemand> sites,
            List<ServiceDemand> services
    ) {
    }
}
