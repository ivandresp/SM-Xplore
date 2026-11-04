package com.smxplore.sitesandservices.infrastructure.adapters.out.kafka;

import com.smxplore.sitesandservices.AbstractKafkaMongoPostgresIntegrationTest;
import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryScope;
import com.smxplore.sitesandservices.domain.ports.in.CreateCategoryUseCase;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.OutboxEventDocument;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OutboxRelayIntegrationTests extends AbstractKafkaMongoPostgresIntegrationTest {

    @Autowired
    private CreateCategoryUseCase createCategoryUseCase;

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    @Test
    void relayPublishesThePendingOutboxEventToKafka() {
        Category category = createCategoryUseCase.handle(
                        Category.builder()
                                .name("Categoría Kafka")
                                .scope(CategoryScope.GENERAL)
                                .build(),
                        "outbox-test")
                .block();

        assertThat(category).isNotNull();

        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of("catalog.category.changed"));
            ConsumerRecord<String, String> record = awaitRecord(
                    consumer,
                    category.categoryId().toString(),
                    Duration.ofSeconds(30)
            );

            assertThat(record.key()).isEqualTo(category.categoryId().toString());
            assertThat(record.value()).contains("\"eventType\":\"catalog.category.changed\"");
            assertThat(record.value()).contains("\"producer\":\"ms-04\"");
            assertThat(record.value()).contains(category.categoryId().toString());
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            OutboxEventDocument event = mongoTemplate.findOne(
                            Query.query(Criteria.where("aggregateId").is(category.categoryId().toString())),
                            OutboxEventDocument.class)
                    .block();
            assertThat(event).isNotNull();
            assertThat(event.getStatus()).isEqualTo("PUBLISHED");
            assertThat(event.getPublishedAt()).isNotNull();
        });
    }

    private KafkaConsumer<String, String> consumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return new KafkaConsumer<>(properties);
    }

    private ConsumerRecord<String, String> awaitRecord(
            KafkaConsumer<String, String> consumer,
            String expectedKey,
            Duration timeout
    ) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (expectedKey.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No Kafka record with key " + expectedKey + " received in " + timeout);
    }
}
