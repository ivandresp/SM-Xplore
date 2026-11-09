package com.smxplore.users.support;

import com.smxplore.users.application.port.out.TransactionPort;
import reactor.core.publisher.Mono;

public final class FakeTransactionPort implements TransactionPort {

    @Override
    public <T> Mono<T> execute(Mono<T> action) {
        return action;
    }
}
