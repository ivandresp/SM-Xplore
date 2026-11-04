package com.smxplore.sitesandservices.application.reservation;

import com.smxplore.sitesandservices.application.support.ReservationAccess;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.exception.ResourceNotFoundException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.ports.in.GetReservationUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ReservationRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ReservationGetService implements GetReservationUseCase {

    private final ReservationRepositoryPort reservationRepository;
    private final ReservationAccess access;

    @Override
    public Mono<Reservation> handle(UUID reservationId, Actor actor) {
        if (reservationId == null) {
            return Mono.error(new InvalidCatalogException("reservationId is required"));
        }
        return reservationRepository.findById(reservationId)
                .switchIfEmpty(Mono.error(
                        new ResourceNotFoundException("Reservation", reservationId)))
                .flatMap(reservation -> access.requireVisible(reservation, actor));
    }
}
