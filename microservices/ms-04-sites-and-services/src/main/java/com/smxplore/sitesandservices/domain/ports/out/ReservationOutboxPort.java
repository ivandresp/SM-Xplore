package com.smxplore.sitesandservices.domain.ports.out;

import com.smxplore.sitesandservices.domain.model.event.DomainEvent;
import reactor.core.publisher.Mono;

public interface ReservationOutboxPort {

    Mono<Void> append(DomainEvent event);
}
