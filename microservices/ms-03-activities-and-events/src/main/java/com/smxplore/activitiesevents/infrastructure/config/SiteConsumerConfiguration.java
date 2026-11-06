package com.smxplore.activitiesevents.infrastructure.config;

import com.smxplore.activitiesevents.infrastructure.adapters.in.kafka.SiteChangedEventDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Turns on the Kafka listener infra only when the site consumer is enabled. Without
 * {@code @EnableKafka} the {@code @KafkaListener} on the snapshot consumer is never registered.
 *
 * <p>The listener uses its own container factory: a message that cannot be applied is retried a
 * couple of times and then published to {@code catalog.site.changed.DLT}, so a malformed or
 * incompatible event never blocks the partition and never disappears without a trace. The main
 * topic keeps advancing because the recoverer commits the offset after the DLT publish.
 */
@Configuration(proxyBeanMethods = false)
@EnableKafka
@ConditionalOnProperty(name = "app.events.site-consumer.enabled", havingValue = "true")
@Slf4j
public class SiteConsumerConfiguration {

    public static final String CONTAINER_FACTORY = "siteSnapshotContainerFactory";
    private static final int DLT_PARTITIONS = 1;
    private static final short DLT_REPLICATION = 1;

    /**
     * Crea el topic de descarte al arrancar: con {@code auto.create.topics.enable=false} (lo normal
     * en produccion) el recoverer fallaria, el offset no avanzaria y la particion quedaria bloqueada.
     */
    @Bean
    public NewTopic siteChangedDeadLetterTopic(
            @Value("${app.events.site-consumer.dlt-suffix:.DLT}") String deadLetterSuffix) {
        return TopicBuilder.name(SiteChangedEventDecoder.EVENT_TYPE + deadLetterSuffix)
                .partitions(DLT_PARTITIONS)
                .replicas(DLT_REPLICATION)
                .build();
    }

    @Bean(name = CONTAINER_FACTORY)
    public ConcurrentKafkaListenerContainerFactory<String, String> siteSnapshotContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
            @Value("${app.events.site-consumer.dlt-suffix:.DLT}") String deadLetterSuffix,
            @Value("${app.events.site-consumer.retry-interval-ms:1000}") long retryIntervalMs,
            @Value("${app.events.site-consumer.retry-max-elapsed-ms:60000}") long retryMaxElapsedMs) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(errorHandler(
                kafkaTemplateProvider, deadLetterSuffix, retryIntervalMs, retryMaxElapsedMs));
        return factory;
    }

    private DefaultErrorHandler errorHandler(
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
            String deadLetterSuffix,
            long retryIntervalMs,
            long retryMaxElapsedMs) {
        var kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        // Reintentos exponenciales (~1 min en total): un hipo de PostgreSQL/R2DBC no debe mandar un
        // evento valido al DLT, pero un fallo permanente no puede bloquear la particion para siempre.
        var backOff = new ExponentialBackOff(retryIntervalMs, 2.0);
        backOff.setMaxElapsedTime(retryMaxElapsedMs);
        if (kafkaTemplate == null) {
            log.warn("No KafkaTemplate available: failed site events are only logged, not dead-lettered");
            var fallback = new DefaultErrorHandler(backOff);
            fallback.setLogLevel(org.springframework.kafka.KafkaException.Level.ERROR);
            return fallback;
        }
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new org.apache.kafka.common.TopicPartition(
                        record.topic() + deadLetterSuffix, -1));
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(
                org.apache.kafka.common.errors.SerializationException.class,
                IllegalArgumentException.class);
        handler.setLogLevel(org.springframework.kafka.KafkaException.Level.WARN);
        return handler;
    }
}
