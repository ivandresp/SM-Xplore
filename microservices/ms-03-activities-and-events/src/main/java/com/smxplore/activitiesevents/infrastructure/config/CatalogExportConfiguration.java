package com.smxplore.activitiesevents.infrastructure.config;

import com.smxplore.activitiesevents.application.catalog.CatalogExportService;
import com.smxplore.activitiesevents.application.outbox.ActivityChangedEventFactory;
import com.smxplore.activitiesevents.application.outbox.EventChangedEventFactory;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.catalog.ExportCatalogUseCasePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CatalogExportConfiguration {

    @Bean
    public ExportCatalogUseCasePort exportCatalogUseCase(ActivityRepositoryPort activities,
                                                         EventRepositoryPort events,
                                                         ActivityChangedEventFactory activityEvents,
                                                         EventChangedEventFactory eventEvents) {
        return new CatalogExportService(activities, events, activityEvents, eventEvents);
    }
}
