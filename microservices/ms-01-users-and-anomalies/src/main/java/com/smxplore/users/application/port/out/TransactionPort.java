package com.smxplore.users.application.port.out;

import reactor.core.publisher.Mono;

public interface TransactionPort {

    <T> Mono<T> execute(Mono<T> action);
}
