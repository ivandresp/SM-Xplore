package com.smxplore.activitiesevents.infrastructure.adapters.in.kafka;

import com.smxplore.activitiesevents.application.events.RefreshSiteSnapshotUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Keeps the site snapshot of the events in sync with ms-04, the owner of the sites. Enabled only
 * with {@code app.events.site-consumer.enabled=true}: without it the snapshot is refreshed on the
 * review-reply path, which re-reads the site over HTTP.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.events.site-consumer.enabled", havingValue = "true")
public class SiteSnapshotKafkaListener {

    private final SiteChangedEventDecoder decoder;
    private final RefreshSiteSnapshotUseCase useCase;

    @Value("${app.events.site-consumer.timeout:PT10S}")
    private Duration timeout;

    @KafkaListener(
            id = "ms-03-site-snapshot",
            topics = SiteChangedEventDecoder.EVENT_TYPE,
            groupId = "${app.events.site-consumer.group:ms-03-site-snapshot}")
    public void onSiteChanged(ConsumerRecord<String, String> record) {
        var change = decoder.decode(record.value());
        if (change.isEmpty()) {
            return;
        }
        var updated = useCase.handle(change.get()).block(timeout);
        log.info("Site {} snapshot refreshed on {} events", change.get().siteId(), updated);
    }
}
