package com.smxplore.sitesandservices.application.support;

import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationFilter;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationStatus;
import reactor.core.publisher.Mono;

public class ReservationAccess {

    public Mono<Actor> requireTourist(Actor actor) {
        return actor.isTourist()
                ? Mono.just(actor)
                : Mono.error(new ForbiddenOperationException(
                        "Only tourists can create reservations"));
    }

    public Mono<Reservation> requireVisible(Reservation reservation, Actor actor) {
        boolean visible = actor.isAdmin()
                || (actor.isTourist() && reservation.belongsToTourist(actor.userId()))
                || (actor.isPrestadora() && reservation.belongsToProvider(actor.userId()));
        return visible
                ? Mono.just(reservation)
                : Mono.error(new ForbiddenOperationException(
                        "Only the tourist, the involved provider or an administrator can view this reservation"));
    }

    public Mono<ReservationFilter> scope(ReservationFilter filter, Actor actor) {
        if (actor.isAdmin()) {
            return Mono.just(filter);
        }
        if (actor.isTourist()) {
            return Mono.just(new ReservationFilter(
                    actor.userId(),
                    null,
                    filter.serviceId(),
                    filter.status(),
                    filter.from(),
                    filter.to(),
                    filter.page(),
                    filter.size()
            ));
        }
        if (actor.isPrestadora()) {
            return Mono.just(new ReservationFilter(
                    filter.touristId(),
                    actor.userId(),
                    filter.serviceId(),
                    filter.status(),
                    filter.from(),
                    filter.to(),
                    filter.page(),
                    filter.size()
            ));
        }
        return Mono.error(new ForbiddenOperationException(
                "Only tourists, service providers or administrators can list reservations"));
    }

    public Mono<Reservation> requireStatusChange(
            Reservation reservation,
            ReservationStatus next,
            Actor actor
    ) {
        if (actor.isAdmin()) {
            return Mono.just(reservation);
        }
        if (actor.isTourist()) {
            if (!reservation.belongsToTourist(actor.userId())) {
                return forbidden("Only the tourist who owns the reservation can change it");
            }
            if (next != ReservationStatus.CANCELLED) {
                return forbidden("Tourists can only cancel their own reservations");
            }
            return Mono.just(reservation);
        }
        if (actor.isPrestadora()) {
            if (!reservation.belongsExclusivelyToProvider(actor.userId())) {
                return forbidden(
                        "A provider can only manage reservations whose services all belong to it");
            }
            return Mono.just(reservation);
        }
        return forbidden("Only tourists, service providers or administrators can change reservations");
    }

    private Mono<Reservation> forbidden(String message) {
        return Mono.error(new ForbiddenOperationException(message));
    }
}
