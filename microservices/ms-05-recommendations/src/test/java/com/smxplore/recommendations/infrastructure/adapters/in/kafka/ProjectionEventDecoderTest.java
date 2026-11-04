package com.smxplore.recommendations.infrastructure.adapters.in.kafka;

import com.smxplore.recommendations.domain.exception.InvalidEventException;
import com.smxplore.recommendations.domain.model.event.EventTopics;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectionEventDecoderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProjectionEventDecoder decoder = new ProjectionEventDecoder(objectMapper);
    private final Path contractsDirectory =
            Path.of(System.getProperty("contracts.dir", "../../contracts/events"));

    @Test
    void acceptsAnExampleWithoutKafkaKey() throws Exception {
        String topic = EventTopics.CATALOG_SITE_CHANGED;
        String json = Files.readString(examplePath(topic));

        var event = decoder.decode(topic, null, json);

        assertThat(event.eventType()).isEqualTo(topic);
    }

    @Test
    void rejectsAKeyThatIsNotTheAggregateId() throws Exception {
        String topic = EventTopics.CATALOG_SITE_CHANGED;
        String json = Files.readString(examplePath(topic));

        assertThatThrownBy(() -> decoder.decode(topic, "another-id", json))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("aggregateId");
    }

    @Test
    void rejectsATopicThatDoesNotMatchTheEventType() throws Exception {
        String json = Files.readString(examplePath(EventTopics.CATALOG_SITE_CHANGED));

        assertThatThrownBy(() -> decoder.decode(
                EventTopics.REVIEWS_REVIEW_CHANGED, null, json))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("does not match topic");
    }

    @Test
    void rejectsAnUnsupportedEventVersion() throws Exception {
        String topic = EventTopics.CATALOG_SITE_CHANGED;
        ObjectNode root = (ObjectNode) objectMapper.readTree(Files.readString(examplePath(topic)));
        root.put("eventVersion", 2);

        assertThatThrownBy(() -> decoder.decode(topic, null, objectMapper.writeValueAsString(root)))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("eventVersion");
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> decoder.decode(
                EventTopics.CATALOG_SITE_CHANGED, null, "{\"eventType\":"))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("valid JSON");
    }

    private Path examplePath(String topic) {
        return contractsDirectory.resolve("examples").resolve(topic + ".v1.example.json");
    }
}
