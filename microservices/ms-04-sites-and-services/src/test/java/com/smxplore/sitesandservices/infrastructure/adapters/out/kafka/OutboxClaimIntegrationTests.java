package com.smxplore.sitesandservices.infrastructure.adapters.out.kafka;

import com.smxplore.sitesandservices.AbstractMongoPostgresIntegrationTest;
import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryScope;
import com.smxplore.sitesandservices.domain.ports.in.CreateCategoryUseCase;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.OutboxEventDocument;
import com.smxplore.sitesandservices.infrastructure.config.OutboxProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxClaimIntegrationTests extends AbstractMongoPostgresIntegrationTest {

    @Autowired
    private CreateCategoryUseCase createCategoryUseCase;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    @Autowired
    private OutboxProperties properties;

    @Autowired
    private OutboxMetrics metrics;

    @Autowired
    private Clock clock;

    @Test
    void twoRelayInstancesPublishEachCatalogEventOnlyOnce() {
        Category category = createCategoryUseCase.handle(
                        Category.builder()
                                .name("Categoría claim")
                                .scope(CategoryScope.GENERAL)
                                .build(),
                        "catalog-claim-test")
                .block();
        assertThat(category).isNotNull();

        CountingPublisher publisher = new CountingPublisher(category.categoryId());
        OutboxRelay first = new OutboxRelay(
                mongoTemplate, publisher, properties, metrics, clock);
        OutboxRelay second = new OutboxRelay(
                mongoTemplate, publisher, properties, metrics, clock);

        Mono.whenDelayError(first.relayOnce(), second.relayOnce())
                .block(Duration.ofSeconds(30));

        assertThat(publisher.count()).isEqualTo(1);
        OutboxEventDocument event = mongoTemplate.findOne(
                        Query.query(Criteria.where("aggregateId")
                                .is(category.categoryId().toString())),
                        OutboxEventDocument.class)
                .block();
        assertThat(event).isNotNull();
        assertThat(event.getStatus()).isEqualTo("PUBLISHED");
    }

    private static final class CountingPublisher extends KafkaOutboxPublisher {

        private final UUID targetAggregateId;
        private final AtomicInteger count = new AtomicInteger();

        private CountingPublisher(UUID targetAggregateId) {
            super(null);
            this.targetAggregateId = targetAggregateId;
        }

        @Override
        public Mono<Void> publish(String eventType, String aggregateId, String eventJson) {
            if (targetAggregateId.toString().equals(aggregateId)) {
                count.incrementAndGet();
                return Mono.delay(Duration.ofMillis(250)).then();
            }
            return Mono.empty();
        }

        private int count() {
            return count.get();
        }
    }
}
