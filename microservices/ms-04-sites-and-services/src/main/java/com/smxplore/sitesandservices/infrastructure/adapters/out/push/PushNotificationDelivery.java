package com.smxplore.sitesandservices.infrastructure.adapters.out.push;

import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.ports.out.NotificationDeliveryPort;
import com.smxplore.sitesandservices.infrastructure.config.NotificationPushProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Canal push de notificaciones. Hoy no hay proveedor externo (FCM/APNs) integrado, así
 * que este adaptador solo deja rastro del intento: log estructurado y contador de
 * Micrometer. Es el punto de extensión previsto para conectar un proveedor real; cuando
 * exista, el envío debe implementarse aquí manteniendo el contrato reactivo del puerto.
 *
 * <p>La bandeja de notificaciones en MongoDB sigue siendo la fuente de verdad: este canal
 * nunca la reemplaza, solo la refleja. Si el canal está deshabilitado no se registra
 * ningún intento.</p>
 */
@Slf4j
@Component
public class PushNotificationDelivery implements NotificationDeliveryPort {

    static final String ATTEMPT_COUNTER = "notifications.push.attempts";
    static final String SKIPPED_COUNTER = "notifications.push.skipped";

    private final NotificationPushProperties properties;
    private final Counter attempts;
    private final Counter skipped;

    public PushNotificationDelivery(
            NotificationPushProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.attempts = meterRegistry.counter(ATTEMPT_COUNTER);
        this.skipped = meterRegistry.counter(SKIPPED_COUNTER);
    }

    @Override
    public Mono<Void> deliver(Notification notification) {
        if (!properties.isEnabled()) {
            skipped.increment();
            log.debug(
                    "notification_push_skipped notificationId={} recipientId={} recipientRole={} type={} reason=channel_disabled",
                    notification.notificationId(),
                    notification.recipientId(),
                    notification.recipientRole(),
                    notification.type());
            return Mono.empty();
        }

        attempts.increment();
        log.info(
                "notification_push_attempt notificationId={} reservationId={} recipientId={} recipientRole={} type={} provider={} status=not_configured",
                notification.notificationId(),
                notification.reservationId(),
                notification.recipientId(),
                notification.recipientRole(),
                notification.type(),
                properties.getProvider());
        return Mono.empty();
    }
}
