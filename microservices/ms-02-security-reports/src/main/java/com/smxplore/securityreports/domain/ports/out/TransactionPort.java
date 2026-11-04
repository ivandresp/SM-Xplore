package com.smxplore.securityreports.domain.ports.out;

import reactor.core.publisher.Mono;

public interface TransactionPort {

    <T> Mono<T> execute(Mono<T> work);
}
