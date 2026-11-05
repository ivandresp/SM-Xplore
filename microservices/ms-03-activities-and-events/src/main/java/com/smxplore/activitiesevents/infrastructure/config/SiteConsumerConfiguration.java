package com.smxplore.activitiesevents.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Turns on the Kafka listener infra only when the site consumer is enabled. Without
 * {@code @EnableKafka} the {@code @KafkaListener} on the snapshot consumer is never registered.
 */
@Configuration(proxyBeanMethods = false)
@EnableKafka
@ConditionalOnProperty(name = "app.events.site-consumer.enabled", havingValue = "true")
public class SiteConsumerConfiguration {
}
