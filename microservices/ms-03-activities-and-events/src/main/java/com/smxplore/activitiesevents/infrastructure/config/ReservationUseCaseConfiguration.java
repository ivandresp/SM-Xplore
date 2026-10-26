package com.smxplore.activitiesevents.infrastructure.config;

import com.smxplore.activitiesevents.application.reservations.CancelReservationUseCase;
import com.smxplore.activitiesevents.application.reservations.ConsultOneReservationUseCase;
import com.smxplore.activitiesevents.application.reservations.ConsultReservationsByGuideUseCase;
import com.smxplore.activitiesevents.application.reservations.ConsultReservationsByItemUseCase;
import com.smxplore.activitiesevents.application.reservations.ConsultReservationsByStatusUseCase;
import com.smxplore.activitiesevents.application.reservations.ConsultReservationsByTouristUseCase;
import com.smxplore.activitiesevents.application.reservations.CreateReservationUseCase;
import com.smxplore.activitiesevents.application.reservations.RegisterReservationAttendanceUseCase;
import com.smxplore.activitiesevents.application.reservations.RegisterReservationNoShowUseCase;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.CancelReservationUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultOneReservationUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByGuideUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByItemUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByStatusUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByTouristUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.CreateReservationUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.RegisterReservationAttendanceUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.RegisterReservationNoShowUseCasePort;
import com.smxplore.activitiesevents.infrastructure.transaction.TransactionalCreateReservationUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Los casos de uso que mutan la reserva se envuelven en una transacción y el
 * caso de uso la lee con lock pesimista ({@code findByIdForUpdate}): así dos
 * operaciones concurrentes sobre la misma reserva no se pisan (lost updates).
 */
@Configuration(proxyBeanMethods = false)
public class ReservationUseCaseConfiguration {

    @Bean
    public CreateReservationUseCasePort createReservationUseCase(ReservationRepositoryPort reservationRepository,
                                                                 ActivityRepositoryPort activityRepository,
                                                                 EventRepositoryPort eventRepository,
                                                                 TransactionalOperator transactionalOperator) {
        var useCase = new CreateReservationUseCase(reservationRepository, activityRepository, eventRepository);
        return new TransactionalCreateReservationUseCase(useCase, transactionalOperator, reservationRepository);
    }

    @Bean
    public ConsultOneReservationUseCasePort consultOneReservationUseCase(ReservationRepositoryPort reservationRepository) {
        return new ConsultOneReservationUseCase(reservationRepository);
    }

    @Bean
    public ConsultReservationsByStatusUseCasePort consultReservationsByStatusUseCase(
            ReservationRepositoryPort reservationRepository) {
        return new ConsultReservationsByStatusUseCase(reservationRepository);
    }

    @Bean
    public ConsultReservationsByTouristUseCasePort consultReservationsByTouristUseCase(
            ReservationRepositoryPort reservationRepository) {
        return new ConsultReservationsByTouristUseCase(reservationRepository);
    }

    @Bean
    public ConsultReservationsByGuideUseCasePort consultReservationsByGuideUseCase(
            ReservationRepositoryPort reservationRepository) {
        return new ConsultReservationsByGuideUseCase(reservationRepository);
    }

    @Bean
    public ConsultReservationsByItemUseCasePort consultReservationsByItemUseCase(
            ReservationRepositoryPort reservationRepository) {
        return new ConsultReservationsByItemUseCase(reservationRepository);
    }

    @Bean
    public CancelReservationUseCasePort cancelReservationUseCase(ReservationRepositoryPort reservationRepository,
                                                                 TransactionalOperator transactionalOperator) {
        var useCase = new CancelReservationUseCase(reservationRepository);
        return (reservationId, staff) -> transactionalOperator.transactional(useCase.handle(reservationId, staff));
    }

    @Bean
    public RegisterReservationAttendanceUseCasePort registerReservationAttendanceUseCase(
            ReservationRepositoryPort reservationRepository, TransactionalOperator transactionalOperator) {
        var useCase = new RegisterReservationAttendanceUseCase(reservationRepository);
        return reservationId -> transactionalOperator.transactional(useCase.handle(reservationId));
    }

    @Bean
    public RegisterReservationNoShowUseCasePort registerReservationNoShowUseCase(
            ReservationRepositoryPort reservationRepository, TransactionalOperator transactionalOperator) {
        var useCase = new RegisterReservationNoShowUseCase(reservationRepository);
        return reservationId -> transactionalOperator.transactional(useCase.handle(reservationId));
    }
}
