package com.smxplore.sitesandservices.domain.ports.out;

import reactor.core.publisher.Mono;

public interface ReservationTransactionPort {

    <T> Mono<T> execute(Mono<T> work);
}
