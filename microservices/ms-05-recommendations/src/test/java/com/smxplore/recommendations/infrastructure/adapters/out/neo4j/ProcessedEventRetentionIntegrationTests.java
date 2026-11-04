package com.smxplore.recommendations.infrastructure.adapters.out.neo4j;

import com.smxplore.recommendations.AbstractNeo4jIntegrationTest;
import com.smxplore.recommendations.domain.ports.in.PurgeProcessedEventsUseCase;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessedEventRetentionIntegrationTests extends AbstractNeo4jIntegrationTest {

    @Autowired
    private PurgeProcessedEventsUseCase purgeProcessedEvents;

    @Autowired
    private Driver driver;

    @Test
    void deletesOnlyTheMarkersOlderThanTheCutoff() {
        String oldMarker = marker(Instant.now().minus(Duration.ofDays(40)));
        String recentMarker = marker(Instant.now().minus(Duration.ofDays(1)));

        Long deleted = purgeProcessedEvents
                .handle(Instant.now().minus(Duration.ofDays(30)), 100, 10)
                .block();

        assertThat(deleted).isPositive();
        assertThat(countOf(oldMarker)).isZero();
        assertThat(countOf(recentMarker)).isEqualTo(1);
    }

    @Test
    void deletesABacklogInBatches() {
        List<String> markers = List.of(
                marker(Instant.now().minus(Duration.ofDays(40))),
                marker(Instant.now().minus(Duration.ofDays(41))),
                marker(Instant.now().minus(Duration.ofDays(42))),
                marker(Instant.now().minus(Duration.ofDays(43)))
        );

        Long deleted = purgeProcessedEvents
                .handle(Instant.now().minus(Duration.ofDays(30)), 3, 10)
                .block();

        assertThat(deleted).isGreaterThanOrEqualTo(4L);
        assertThat(markers).allSatisfy(marker -> assertThat(countOf(marker)).isZero());
    }

    private String marker(Instant processedAt) {
        String eventId = UUID.randomUUID().toString();
        try (Session session = driver.session()) {
            session.run("""
                            MERGE (p:ProcessedEvent {eventId: $eventId})
                            SET p.eventType = $eventType,
                                p.aggregateId = $aggregateId,
                                p.occurredAt = datetime($occurredAt),
                                p.processedAt = datetime($processedAt),
                                p.outcome = 'APPLIED'
                            """, Map.of(
                            "eventId", eventId,
                            "eventType", "catalog.site.changed",
                            "aggregateId", UUID.randomUUID().toString(),
                            "occurredAt", processedAt.toString(),
                            "processedAt", processedAt.toString()
                    ))
                    .consume();
        }
        return eventId;
    }

    private long countOf(String eventId) {
        try (Session session = driver.session()) {
            return session.run("""
                            MATCH (p:ProcessedEvent {eventId: $eventId})
                            RETURN count(p) AS count
                            """, Map.of("eventId", eventId))
                    .single()
                    .get("count")
                    .asLong();
        }
    }
}
