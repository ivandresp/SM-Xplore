package com.smxplore.recommendations.infrastructure.adapters.in.kafka;

import com.smxplore.recommendations.domain.model.event.EventTopics;
import com.smxplore.recommendations.domain.model.event.ProjectedEvent;
import com.smxplore.recommendations.domain.model.event.ProjectionResult;
import com.smxplore.recommendations.domain.ports.in.HandleProjectionEventUseCase;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.events.consumer.enabled", havingValue = "true")
public class EventKafkaListener {

    private final ProjectionEventDecoder decoder;
    private final HandleProjectionEventUseCase useCase;
    private final EventConsumerMetrics metrics;
    private final EventConsumerProperties properties;

    @KafkaListener(
            id = "ms-05-domain-events",
            topics = {
                    EventTopics.CATALOG_SITE_CHANGED,
                    EventTopics.CATALOG_SERVICE_CHANGED,
                    EventTopics.CATALOG_CATEGORY_CHANGED,
                    EventTopics.ACTIVITIES_EVENT_CHANGED,
                    EventTopics.ACTIVITIES_ACTIVITY_CHANGED,
                    EventTopics.REVIEWS_REVIEW_CHANGED
            },
            groupId = "${spring.kafka.consumer.group-id:ms-05-recommendations}",
            containerFactory = "eventKafkaListenerContainerFactory"
    )
    public void onEvent(ConsumerRecord<String, String> record) {
        String eventType;
        try {
            ProjectedEvent event = decoder.decode(record.topic(), record.key(), record.value());
            eventType = event.eventType();
            ProjectionResult result = useCase.handle(event).block(properties.getProcessingTimeout());
            if (result == null) {
                throw new IllegalStateException("Projection did not return a result");
            }
            metrics.recordProcessed(eventType, result);
        } catch (RuntimeException exception) {
            metrics.recordFailure(record.topic());
            throw exception;
        }
    }
}
