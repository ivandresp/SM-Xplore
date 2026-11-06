package com.smxplore.sitesandservices.infrastructure.adapters.out.postgres;

import com.smxplore.sitesandservices.domain.ports.out.ReservationTransactionPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
public class PostgresReservationTransactionAdapter implements ReservationTransactionPort {

    private final TransactionalOperator transactionalOperator;

    public PostgresReservationTransactionAdapter(
            @Qualifier("postgresTransactionalOperator") TransactionalOperator transactionalOperator
    ) {
        this.transactionalOperator = transactionalOperator;
    }

    @Override
    public <T> Mono<T> execute(Mono<T> work) {
        return transactionalOperator.transactional(work);
    }
}
