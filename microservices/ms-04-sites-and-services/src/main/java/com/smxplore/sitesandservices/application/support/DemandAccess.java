package com.smxplore.sitesandservices.application.support;

import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import reactor.core.publisher.Mono;

public class DemandAccess {

    public Mono<Actor> requireReader(Actor actor) {
        return actor.isAdmin() || actor.isGestora() || actor.isPrestadora()
                ? Mono.just(actor)
                : Mono.error(new ForbiddenOperationException(
                        "Only service providers, destination managers or administrators "
                                + "can view demand indicators"));
    }
}
