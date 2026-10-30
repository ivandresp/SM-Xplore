package com.smxplore.users.infrastructure.config;

import com.smxplore.users.application.port.out.TransactionPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
public class ReactiveTransactionAdapter implements TransactionPort {

    private final TransactionalOperator operator;

    public ReactiveTransactionAdapter(ReactiveTransactionManager transactionManager) {
        this.operator = TransactionalOperator.create(transactionManager);
    }

    @Override
    public <T> Mono<T> execute(Mono<T> action) {
        return operator.transactional(action);
    }
}
