package com.smxplore.activitiesevents.domain.ports.usecases.reservation;

import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import reactor.core.publisher.Mono;

public interface ConsultReservationsByStatusUseCasePort {
    Mono<PageResult<Reservation>> handle(ReservationStatus status, int page, int size);
}
