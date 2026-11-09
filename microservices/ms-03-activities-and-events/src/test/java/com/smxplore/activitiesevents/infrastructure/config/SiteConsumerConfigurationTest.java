package com.smxplore.activitiesevents.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Regresion de MS03-15: la forma del topic de descarte ya no esta fijada en constantes. Con un solo
 * broker el valor por defecto sigue siendo 1/1 (lo que exige el stack local y el de produccion),
 * pero un despliegue con varios brokers puede subir particiones y replica por configuracion.
 */
class SiteConsumerConfigurationTest {

    private final SiteConsumerConfiguration configuration = new SiteConsumerConfiguration();

    @Test
    void keepsTheSingleBrokerShapeByDefault() {
        var topic = configuration.siteChangedDeadLetterTopic(".DLT", 1, (short) 1);

        assertThat(topic.name()).isEqualTo("catalog.site.changed.DLT");
        assertThat(topic.numPartitions()).isEqualTo(1);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    void usesTheConfiguredPartitionsAndReplication() {
        var topic = configuration.siteChangedDeadLetterTopic(".DLT", 3, (short) 3);

        assertThat(topic.name()).isEqualTo("catalog.site.changed.DLT");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 3);
    }

    @Test
    void honoursTheConfiguredSuffix() {
        var topic = configuration.siteChangedDeadLetterTopic(".dlq", 1, (short) 1);

        assertThat(topic.name()).isEqualTo("catalog.site.changed.dlq");
    }
}
