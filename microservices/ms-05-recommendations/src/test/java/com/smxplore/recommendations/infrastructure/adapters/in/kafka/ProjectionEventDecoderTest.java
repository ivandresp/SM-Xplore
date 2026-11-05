package com.smxplore.recommendations.infrastructure.adapters.in.kafka;

import com.smxplore.recommendations.domain.exception.InvalidEventException;
import com.smxplore.recommendations.domain.model.catalog.ContentStatus;
import com.smxplore.recommendations.domain.model.event.ActivityChangedEvent;
import com.smxplore.recommendations.domain.model.event.EventItemChangedEvent;
import com.smxplore.recommendations.domain.model.event.EventTopics;
import com.smxplore.recommendations.domain.model.event.SiteChangedEvent;
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
        ObjectNode root = (ObjectNode) objectMapper.readTree(Files.readString(examplePath(topic, 2)));
        root.put("eventVersion", 3);

        assertThatThrownBy(() -> decoder.decode(topic, null, objectMapper.writeValueAsString(root)))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("eventVersion");
    }

    @Test
    void acceptsV2SiteActivityAndEventExamples() throws Exception {
        String siteJson = Files.readString(examplePath(EventTopics.CATALOG_SITE_CHANGED, 2));
        var site = decoder.decode(
                EventTopics.CATALOG_SITE_CHANGED,
                objectMapper.readTree(siteJson).get("aggregateId").asString(),
                siteJson);
        assertThat(site).isInstanceOf(SiteChangedEvent.class);
        assertThat(site.metadata().eventVersion()).isEqualTo(2);

        String activityJson = Files.readString(examplePath(EventTopics.ACTIVITIES_ACTIVITY_CHANGED, 2));
        var activity = decoder.decode(
                EventTopics.ACTIVITIES_ACTIVITY_CHANGED,
                objectMapper.readTree(activityJson).get("aggregateId").asString(),
                activityJson);
        assertThat(activity).isInstanceOf(ActivityChangedEvent.class);
        ActivityChangedEvent activityEvent = (ActivityChangedEvent) activity;
        assertThat(activityEvent.categoryName()).isEqualTo("Deportes acuáticos");
        assertThat(activityEvent.categoryId()).isNotNull();
        assertThat(activityEvent.latitude()).isNull();
        assertThat(activityEvent.longitude()).isNull();
        assertThat(activityEvent.status()).isEqualTo(ContentStatus.PUBLISHED);

        String eventJson = Files.readString(examplePath(EventTopics.ACTIVITIES_EVENT_CHANGED, 2));
        var eventItem = decoder.decode(
                EventTopics.ACTIVITIES_EVENT_CHANGED,
                objectMapper.readTree(eventJson).get("aggregateId").asString(),
                eventJson);
        assertThat(eventItem).isInstanceOf(EventItemChangedEvent.class);
        EventItemChangedEvent eventItemEvent = (EventItemChangedEvent) eventItem;
        assertThat(eventItemEvent.categoryName()).isEqualTo("Cultura");
        assertThat(eventItemEvent.categoryId()).isNotNull();
        assertThat(eventItemEvent.status()).isEqualTo(ContentStatus.POSTPONED);
    }

    @Test
    void rejectsV2WhenTheSiteManagerIsMissing() throws Exception {
        String topic = EventTopics.CATALOG_SITE_CHANGED;
        ObjectNode root = (ObjectNode) objectMapper.readTree(Files.readString(examplePath(topic, 2)));
        ((ObjectNode) root.get("payload")).remove("managerId");

        assertThatThrownBy(() -> decoder.decode(topic, null, objectMapper.writeValueAsString(root)))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("managerId");
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

    private Path examplePath(String topic, int version) {
        return contractsDirectory.resolve("examples").resolve(topic + ".v" + version + ".example.json");
    }
}
