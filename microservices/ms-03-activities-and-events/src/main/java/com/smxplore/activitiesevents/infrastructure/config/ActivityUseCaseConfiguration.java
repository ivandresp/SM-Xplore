package com.smxplore.activitiesevents.infrastructure.config;

import com.smxplore.activitiesevents.application.activities.CancelActivityUseCase;
import com.smxplore.activitiesevents.application.activities.ChangeActivityStatusUseCase;
import com.smxplore.activitiesevents.application.activities.ConsultActivitiesUseCase;
import com.smxplore.activitiesevents.application.activities.FindActivitiesByCategoryUseCase;
import com.smxplore.activitiesevents.application.activities.FindActivitiesBySiteUseCase;
import com.smxplore.activitiesevents.application.activities.ManageAvailabilityUseCase;
import com.smxplore.activitiesevents.application.activities.ManageSlotsUseCase;
import com.smxplore.activitiesevents.application.activities.RegisterActivityUseCase;
import com.smxplore.activitiesevents.application.activities.UpdateActivityUseCase;
import com.smxplore.activitiesevents.application.activities.ViewActivityDetailsUseCase;
import com.smxplore.activitiesevents.application.outbox.ActivityChangedEventFactory;
import com.smxplore.activitiesevents.application.outbox.OutboxPublisher;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.CancelActivityUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ChangeActivityStatusUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ConsultActivitiesUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.FindActivitiesByCategoryUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.FindActivitiesBySiteUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ManageAvailabilityUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ManageSlotsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.RegisterActivityUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.UpdateActivityUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ViewActivityDetailsUseCasePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Los casos de uso que escriben una actividad publican {@code activities.activity.changed} en el
 * outbox. La actividad y su evento viven en el mismo PostgreSQL, así que cada uno corre dentro de
 * una transacción: si el INSERT del outbox falla, la escritura de la actividad se revierte y no
 * queda una fila sin evento.
 */
@Configuration(proxyBeanMethods = false)
public class ActivityUseCaseConfiguration {

    @Bean
    public RegisterActivityUseCasePort registerActivityUseCase(ActivityRepositoryPort activityRepository,
                                                               ActivityChangedEventFactory activityEvents,
                                                               OutboxPublisher publisher,
                                                               TransactionalOperator transactionalOperator) {
        var useCase = new RegisterActivityUseCase(activityRepository, activityEvents, publisher);
        return activity -> transactionalOperator.transactional(useCase.handle(activity));
    }

    @Bean
    public ViewActivityDetailsUseCasePort viewActivityDetailsUseCase(ActivityRepositoryPort activityRepository) {
        return new ViewActivityDetailsUseCase(activityRepository);
    }

    @Bean
    public ConsultActivitiesUseCasePort consultActivitiesUseCase(ActivityRepositoryPort activityRepository) {
        return new ConsultActivitiesUseCase(activityRepository);
    }

    @Bean
    public FindActivitiesByCategoryUseCasePort findActivitiesByCategoryUseCase(ActivityRepositoryPort activityRepository) {
        return new FindActivitiesByCategoryUseCase(activityRepository);
    }

    @Bean
    public FindActivitiesBySiteUseCasePort findActivitiesBySiteUseCase(ActivityRepositoryPort activityRepository) {
        return new FindActivitiesBySiteUseCase(activityRepository);
    }

    @Bean
    public UpdateActivityUseCasePort updateActivityUseCase(ActivityRepositoryPort activityRepository,
                                                           ActivityChangedEventFactory activityEvents,
                                                           OutboxPublisher publisher,
                                                           TransactionalOperator transactionalOperator) {
        var useCase = new UpdateActivityUseCase(activityRepository, activityEvents, publisher);
        return (activityId, name, category, description, price) -> transactionalOperator
                .transactional(useCase.handle(activityId, name, category, description, price));
    }

    @Bean
    public ManageAvailabilityUseCasePort manageAvailabilityUseCase(ActivityRepositoryPort activityRepository,
                                                                   ReservationRepositoryPort reservationRepository) {
        return new ManageAvailabilityUseCase(activityRepository, reservationRepository);
    }

    @Bean
    public ManageSlotsUseCasePort manageSlotsUseCase(ActivityRepositoryPort activityRepository) {
        return new ManageSlotsUseCase(activityRepository);
    }

    @Bean
    public CancelActivityUseCasePort cancelActivityUseCase(ActivityRepositoryPort activityRepository,
                                                           ActivityChangedEventFactory activityEvents,
                                                           OutboxPublisher publisher,
                                                           TransactionalOperator transactionalOperator) {
        var useCase = new CancelActivityUseCase(activityRepository, activityEvents, publisher);
        return activityId -> transactionalOperator.transactional(useCase.handle(activityId));
    }

    @Bean
    public ChangeActivityStatusUseCasePort changeActivityStatusUseCase(ActivityRepositoryPort activityRepository,
                                                                       ActivityChangedEventFactory activityEvents,
                                                                       OutboxPublisher publisher,
                                                                       TransactionalOperator transactionalOperator) {
        var useCase = new ChangeActivityStatusUseCase(activityRepository, activityEvents, publisher);
        return (activityId, newStatus) -> transactionalOperator.transactional(
                useCase.handle(activityId, newStatus));
    }
}
