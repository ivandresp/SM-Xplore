package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo;

import com.smxplore.sitesandservices.domain.ports.out.TransactionPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
public class MongoTransactionAdapter implements TransactionPort {

    private final TransactionalOperator transactionalOperator;

    public MongoTransactionAdapter(
            @Qualifier("mongoTransactionalOperator") TransactionalOperator transactionalOperator
    ) {
        this.transactionalOperator = transactionalOperator;
    }

    @Override
    public <T> Mono<T> execute(Mono<T> work) {
        return transactionalOperator.transactional(work);
    }
}
