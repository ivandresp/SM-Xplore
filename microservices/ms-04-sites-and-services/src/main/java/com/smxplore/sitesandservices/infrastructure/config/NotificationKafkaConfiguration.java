package com.smxplore.sitesandservices.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration(proxyBeanMethods = false)
@EnableKafka
@ConditionalOnProperty(
        name = "app.notifications.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NotificationKafkaConfiguration {
}
