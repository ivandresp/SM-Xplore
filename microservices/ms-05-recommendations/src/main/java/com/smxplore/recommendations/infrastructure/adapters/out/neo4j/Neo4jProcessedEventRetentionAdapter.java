package com.smxplore.recommendations.infrastructure.adapters.out.neo4j;

import com.smxplore.recommendations.domain.ports.out.ProcessedEventRetentionPort;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.neo4j.driver.reactivestreams.ReactiveTransactionContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class Neo4jProcessedEventRetentionAdapter implements ProcessedEventRetentionPort {

    private final Driver driver;

    @Override
    public Mono<Long> deleteProcessedBefore(Instant cutoff, int batchSize) {
        Map<String, Object> params = Map.of(
                "cutoff", cutoff.toString(),
                "batchSize", batchSize
        );
        return Mono.usingWhen(
                Mono.fromSupplier(() -> driver.session(ReactiveSession.class)),
                session -> Flux.from(session.executeWrite(transaction -> deleteBatch(transaction, params)))
                        .singleOrEmpty(),
                session -> Flux.from(session.close()).then()
        );
    }

    private Mono<Long> deleteBatch(ReactiveTransactionContext transaction, Map<String, Object> params) {
        return Mono.from(transaction.run(Neo4jProjectionQueries.PRUNE_PROCESSED, params))
                .flatMap(result -> Flux.from(result.records()).collectList())
                .map(records -> records.isEmpty() ? 0L : records.getFirst().get("deleted").asLong(0L));
    }
}
