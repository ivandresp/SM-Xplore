package com.smxplore.securityreports.domain.ports.out;

import com.smxplore.securityreports.domain.model.event.DomainEvent;
import reactor.core.publisher.Mono;

public interface OutboxPort {

    Mono<Void> append(DomainEvent event);
}
