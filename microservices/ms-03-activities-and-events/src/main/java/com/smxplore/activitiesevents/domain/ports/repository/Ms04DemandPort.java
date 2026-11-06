package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.indicator.DemandBucket;
import com.smxplore.activitiesevents.domain.model.indicator.DemandComparison;
import com.smxplore.activitiesevents.domain.model.indicator.DemandDistribution;
import com.smxplore.activitiesevents.domain.model.indicator.DemandFilter;
import com.smxplore.activitiesevents.domain.model.indicator.DemandSummary;
import com.smxplore.activitiesevents.domain.model.indicator.ServiceDemand;
import com.smxplore.activitiesevents.domain.model.indicator.SiteDemand;
import com.smxplore.activitiesevents.domain.model.indicator.ZoneDemand;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Read-only view of the ms-04 demand indicators. ms-03 uses it to complete the destination
 * manager's consolidated view with service demand; ms-04 remains the owner of services.
 */
public interface Ms04DemandPort {

    Mono<ServicesDemand> fetch(DemandFilter filter, String bearerToken);

    record ServicesDemand(
            DemandSummary summary,
            DemandComparison comparison,
            List<DemandBucket> timeline,
            List<DemandDistribution> weekdays,
            List<DemandDistribution> hours,
            List<ZoneDemand> zones,
            List<SiteDemand> sites,
            List<ServiceDemand> services
    ) {
        public ServicesDemand {
            timeline = timeline == null ? List.of() : List.copyOf(timeline);
            weekdays = weekdays == null ? List.of() : List.copyOf(weekdays);
            hours = hours == null ? List.of() : List.copyOf(hours);
            zones = zones == null ? List.of() : List.copyOf(zones);
            sites = sites == null ? List.of() : List.copyOf(sites);
            services = services == null ? List.of() : List.copyOf(services);
        }
    }
}
