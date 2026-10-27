package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo;

import com.smxplore.sitesandservices.domain.ports.out.TransactionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MongoTransactionAdapter implements TransactionPort {

    private final TransactionalOperator transactionalOperator;

    @Override
    public <T> Mono<T> execute(Mono<T> work) {
        return transactionalOperator.transactional(work);
    }
}
