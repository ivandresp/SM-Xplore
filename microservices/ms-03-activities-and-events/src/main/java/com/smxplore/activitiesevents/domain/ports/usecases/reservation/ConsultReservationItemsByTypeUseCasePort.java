package com.smxplore.activitiesevents.domain.ports.usecases.reservation;

import com.smxplore.activitiesevents.domain.model.reservation.ReservationItem;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import reactor.core.publisher.Flux;

public interface ConsultReservationItemsByTypeUseCasePort {
    Flux<ReservationItem> handle(ReservationItemType type, int limit);
}
