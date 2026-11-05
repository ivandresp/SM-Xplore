package com.smxplore.activitiesevents.infrastructure.config;

import com.smxplore.activitiesevents.application.outbox.BestEffortPublisher;
import com.smxplore.activitiesevents.application.outbox.EventChangedEventFactory;
import com.smxplore.activitiesevents.application.events.ChangeEventStatusUseCase;
import com.smxplore.activitiesevents.application.events.ConsultEventsUseCase;
import com.smxplore.activitiesevents.application.events.EstimateEventAttendanceUseCase;
import com.smxplore.activitiesevents.application.events.RegisterEventUseCase;
import com.smxplore.activitiesevents.application.events.ResolveEventSiteUseCase;
import com.smxplore.activitiesevents.application.events.SearchEventsUseCase;
import com.smxplore.activitiesevents.application.events.ValidateEventExistsUseCase;
import com.smxplore.activitiesevents.application.events.ViewEventDetailsUseCase;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.ChangeEventStatusUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.ConsultEventsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.EstimateEventAttendanceUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.RegisterEventUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.SearchEventsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.ValidateEventExistsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.ViewEventDetailsUseCasePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EventUseCaseConfiguration {

    @Bean
    public RegisterEventUseCasePort registerEventUseCase(EventRepositoryPort eventRepository,
                                                         EventChangedEventFactory eventEvents,
                                                         BestEffortPublisher publisher) {
        return new RegisterEventUseCase(eventRepository, eventEvents, publisher);
    }

    @Bean
    public ResolveEventSiteUseCase resolveEventSiteUseCase(SiteCatalogPort siteCatalog) {
        return new ResolveEventSiteUseCase(siteCatalog);
    }

    @Bean
    public ViewEventDetailsUseCasePort viewEventDetailsUseCase(EventRepositoryPort eventRepository) {
        return new ViewEventDetailsUseCase(eventRepository);
    }

    @Bean
    public ConsultEventsUseCasePort consultEventsUseCase(EventRepositoryPort eventRepository) {
        return new ConsultEventsUseCase(eventRepository);
    }

    @Bean
    public SearchEventsUseCasePort searchEventsUseCase(EventRepositoryPort eventRepository) {
        return new SearchEventsUseCase(eventRepository);
    }

    @Bean
    public ChangeEventStatusUseCasePort changeEventStatusUseCase(EventRepositoryPort eventRepository,
                                                                 EventChangedEventFactory eventEvents,
                                                                 BestEffortPublisher publisher) {
        return new ChangeEventStatusUseCase(eventRepository, eventEvents, publisher);
    }

    @Bean
    public EstimateEventAttendanceUseCasePort estimateEventAttendanceUseCase(EventRepositoryPort eventRepository) {
        return new EstimateEventAttendanceUseCase(eventRepository);
    }

    @Bean
    public ValidateEventExistsUseCasePort validateEventExistsUseCase(EventRepositoryPort eventRepository) {
        return new ValidateEventExistsUseCase(eventRepository);
    }
}
