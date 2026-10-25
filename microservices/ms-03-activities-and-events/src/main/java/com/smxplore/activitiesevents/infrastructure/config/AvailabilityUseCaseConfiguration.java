package com.smxplore.activitiesevents.infrastructure.config;

import com.smxplore.activitiesevents.application.availability.ConsultAvailabilityUseCase;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationItemRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.availability.ConsultAvailabilityUseCasePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AvailabilityUseCaseConfiguration {

    @Bean
    public ConsultAvailabilityUseCasePort consultAvailabilityUseCase(
            EventRepositoryPort eventRepository,
            ActivityRepositoryPort activityRepository,
            ReservationItemRepositoryPort reservationItemRepository) {
        return new ConsultAvailabilityUseCase(eventRepository, activityRepository, reservationItemRepository);
    }
}
