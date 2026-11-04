package com.smxplore.sitesandservices.domain.ports.out;

import com.smxplore.sitesandservices.domain.model.notification.Notification;
import reactor.core.publisher.Mono;

/**
 * Second delivery channel for notifications. The inbox stored in MongoDB is always written first;
 * this port only mirrors a notification to an external channel (today: email).
 */
public interface NotificationDeliveryPort {

    Mono<Void> deliver(Notification notification);
}
