package com.smxplore.activitiesevents.infrastructure.config;

import com.smxplore.activitiesevents.application.accounts.UnpublishGuideActivitiesUseCase;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ChangeActivityStatusUseCasePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Enciende el consumidor de bloqueos solo cuando la propiedad esta activa (dev/prod). En tests
 * queda apagado para no intentar conectar con un broker ausente.
 */
@Configuration(proxyBeanMethods = false)
@EnableKafka
@ConditionalOnProperty(name = "app.events.account-consumer.enabled", havingValue = "true")
public class AccountConsumerConfiguration {

    @Bean
    public UnpublishGuideActivitiesUseCase unpublishGuideActivitiesUseCase(
            ActivityRepositoryPort activities, ChangeActivityStatusUseCasePort changeStatus) {
        return new UnpublishGuideActivitiesUseCase(activities, changeStatus);
    }
}
