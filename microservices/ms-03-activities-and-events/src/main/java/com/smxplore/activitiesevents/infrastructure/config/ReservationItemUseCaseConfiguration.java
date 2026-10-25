package com.smxplore.activitiesevents.infrastructure.config;

import com.smxplore.activitiesevents.application.reservations.AddReservationItemUseCase;
import com.smxplore.activitiesevents.application.reservations.AddReservationItemsUseCase;
import com.smxplore.activitiesevents.application.reservations.ConsultOneReservationItemUseCase;
import com.smxplore.activitiesevents.application.reservations.ConsultReservationItemsByTypeUseCase;
import com.smxplore.activitiesevents.application.reservations.ConsultReservationItemsUseCase;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationItemRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.AddReservationItemUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.AddReservationItemsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultOneReservationItemUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationItemsByTypeUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationItemsUseCasePort;
import com.smxplore.activitiesevents.infrastructure.transaction.TransactionalAddReservationItemsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration(proxyBeanMethods = false)
public class ReservationItemUseCaseConfiguration {

    @Bean
    public AddReservationItemsUseCasePort addReservationItemsUseCase(ReservationRepositoryPort reservationRepository,
                                                                     ReservationItemRepositoryPort reservationItemRepository,
                                                                     ActivityRepositoryPort activityRepository,
                                                                     EventRepositoryPort eventRepository,
                                                                     TransactionalOperator transactionalOperator) {
        var useCase = new AddReservationItemsUseCase(
                reservationRepository, reservationItemRepository, activityRepository, eventRepository);
        return new TransactionalAddReservationItemsUseCase(useCase, transactionalOperator);
    }

    @Bean
    public AddReservationItemUseCasePort addReservationItemUseCase(
            AddReservationItemsUseCasePort addReservationItemsUseCase) {
        return new AddReservationItemUseCase(addReservationItemsUseCase);
    }

    @Bean
    public ConsultReservationItemsUseCasePort consultReservationItemsUseCase(
            ReservationRepositoryPort reservationRepository,
            ReservationItemRepositoryPort reservationItemRepository) {
        return new ConsultReservationItemsUseCase(reservationRepository, reservationItemRepository);
    }

    @Bean
    public ConsultOneReservationItemUseCasePort consultOneReservationItemUseCase(
            ReservationItemRepositoryPort reservationItemRepository) {
        return new ConsultOneReservationItemUseCase(reservationItemRepository);
    }

    @Bean
    public ConsultReservationItemsByTypeUseCasePort consultReservationItemsByTypeUseCase(
            ReservationItemRepositoryPort reservationItemRepository) {
        return new ConsultReservationItemsByTypeUseCase(reservationItemRepository);
    }
}
