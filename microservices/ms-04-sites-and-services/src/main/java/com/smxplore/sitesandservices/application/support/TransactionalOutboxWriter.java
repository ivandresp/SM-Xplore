package com.smxplore.sitesandservices.application.support;

import com.smxplore.sitesandservices.domain.model.event.DomainEvent;
import com.smxplore.sitesandservices.domain.ports.out.OutboxPort;
import com.smxplore.sitesandservices.domain.ports.out.TransactionPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.function.BiFunction;

@RequiredArgsConstructor
public class TransactionalOutboxWriter {

    private final TransactionPort transaction;
    private final OutboxPort outbox;

    public <T> Mono<T> save(
            Mono<T> saveOperation,
            BiFunction<T, String, DomainEvent> eventFactory,
            String correlationId
    ) {
        return transaction.execute(
                saveOperation.flatMap(saved -> outbox.append(eventFactory.apply(saved, correlationId))
                        .thenReturn(saved))
        );
    }
}
