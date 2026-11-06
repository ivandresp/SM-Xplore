package com.smxplore.activitiesevents.infrastructure.config;

import com.smxplore.activitiesevents.application.indicator.DemandIndicatorService;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.DemandQueryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.Ms04DemandPort;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import com.smxplore.activitiesevents.domain.ports.usecases.indicator.GetDemandIndicatorsUseCasePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class DemandUseCaseConfiguration {

    @Bean
    public ZoneId demandZoneId(@Value("${app.demand.zone-id:America/Bogota}") String zoneId) {
        return ZoneId.of(zoneId);
    }

    @Bean
    public GetDemandIndicatorsUseCasePort getDemandIndicatorsUseCase(
            DemandQueryPort demandQuery,
            ActivityRepositoryPort activityRepository,
            EventRepositoryPort eventRepository,
            SiteCatalogPort siteCatalog,
            Ms04DemandPort ms04Demand,
            ZoneId demandZoneId,
            Clock clock
    ) {
        return new DemandIndicatorService(demandQuery, activityRepository, eventRepository,
                siteCatalog, ms04Demand, demandZoneId, clock);
    }
}
