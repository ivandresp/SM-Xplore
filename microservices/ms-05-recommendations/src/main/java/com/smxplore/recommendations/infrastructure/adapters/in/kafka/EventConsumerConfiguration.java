package com.smxplore.recommendations.infrastructure.adapters.in.kafka;

import com.smxplore.recommendations.domain.exception.InvalidEventException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
@EnableKafka
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.events.consumer.enabled", havingValue = "true")
public class EventConsumerConfiguration {

    private final EventConsumerProperties properties;
    private final EventConsumerMetrics metrics;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> eventKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(properties.getConcurrency());
        factory.setMissingTopicsFatal(false);
        factory.setCommonErrorHandler(eventErrorHandler(kafkaTemplate));
        factory.getContainerProperties().setAckMode(AckMode.RECORD);
        factory.getContainerProperties().setSyncCommits(true);
        factory.getContainerProperties().setPollTimeout(Duration.ofSeconds(3).toMillis());
        return factory;
    }

    @Bean
    public DefaultErrorHandler eventErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer deadLetterRecoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + properties.getDlqSuffix(), -1)
        );
        deadLetterRecoverer.setHeadersFunction(this::deadLetterHeaders);
        deadLetterRecoverer.setFailIfSendResultIsError(true);
        deadLetterRecoverer.setWaitForSendResultTimeout(Duration.ofSeconds(10));

        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            metrics.recordDlq(record.topic());
            deadLetterRecoverer.accept(record, exception);
        };

        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(properties.getMaxRetries());
        backOff.setInitialInterval(properties.getInitialBackoff().toMillis());
        backOff.setMultiplier(properties.getBackoffMultiplier());
        backOff.setMaxInterval(properties.getMaxBackoff().toMillis());

        var errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(InvalidEventException.class);
        errorHandler.setCommitRecovered(true);
        errorHandler.setAckAfterHandle(true);
        return errorHandler;
    }

    private Headers deadLetterHeaders(ConsumerRecord<?, ?> record, Exception exception) {
        Headers headers = new RecordHeaders(record.headers());
        headers.add("x-original-topic", record.topic().getBytes(StandardCharsets.UTF_8));
        headers.add("x-original-partition",
                String.valueOf(record.partition()).getBytes(StandardCharsets.UTF_8));
        headers.add("x-original-offset",
                String.valueOf(record.offset()).getBytes(StandardCharsets.UTF_8));
        headers.add("x-error-type",
                exception.getClass().getName().getBytes(StandardCharsets.UTF_8));
        headers.add("x-error-message",
                String.valueOf(exception.getMessage()).getBytes(StandardCharsets.UTF_8));
        return headers;
    }
}
