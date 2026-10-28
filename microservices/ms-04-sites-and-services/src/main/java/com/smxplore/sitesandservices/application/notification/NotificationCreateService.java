package com.smxplore.sitesandservices.application.notification;

import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.model.notification.NotificationType;
import com.smxplore.sitesandservices.domain.ports.out.NotificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class NotificationCreateService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final NotificationRepositoryPort notificationRepository;
    private final Clock clock;

    public Mono<Void> handle(ReservationChangedMessage message) {
        NotificationType type = typeFor(message.status());
        if (type == null || message.items().isEmpty()) {
            return Mono.empty();
        }
        if (message.items().stream()
                .anyMatch(item -> !"SERVICE".equals(item.itemType()))) {
            return Mono.empty();
        }

        String services = serviceNames(message.items());
        int people = message.items().stream().mapToInt(ReservationChangedMessage.Item::people).sum();
        String date = message.bookedFor() == null
                ? "la fecha indicada"
                : DATE_FORMAT.format(message.bookedFor());

        List<Notification> notifications = new ArrayList<>();
        notifications.add(notification(
                message,
                message.touristId(),
                UserRole.TURISTA,
                type,
                touristTitle(type),
                touristMessage(type, services, date, people)
        ));

        Set<UUID> providerIds = message.items().stream()
                .map(ReservationChangedMessage.Item::providerId)
                .filter(providerId -> providerId != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (UUID providerId : providerIds) {
            notifications.add(notification(
                    message,
                    providerId,
                    UserRole.PRESTADORA_SERVICIOS,
                    type,
                    providerTitle(type),
                    providerMessage(type, services, date, people)
            ));
        }

        return Flux.fromIterable(notifications)
                .concatMap(notificationRepository::saveIfAbsent)
                .then();
    }

    private Notification notification(
            ReservationChangedMessage message,
            UUID recipientId,
            UserRole recipientRole,
            NotificationType type,
            String title,
            String messageText
    ) {
        UUID notificationId = UUID.nameUUIDFromBytes((
                message.eventId() + "|" + recipientId + "|" + type.name()
        ).getBytes(StandardCharsets.UTF_8));
        Notification notification = Notification.builder()
                .notificationId(notificationId)
                .recipientId(recipientId)
                .recipientRole(recipientRole)
                .reservationId(message.reservationId())
                .eventId(message.eventId())
                .type(type)
                .title(title)
                .message(truncate(messageText, 1000))
                .createdAt(Instant.now(clock))
                .build();
        notification.validate();
        return notification;
    }

    private NotificationType typeFor(String status) {
        return switch (status) {
            case "CONFIRMED" -> NotificationType.RESERVATION_CONFIRMED;
            case "CANCELLED" -> NotificationType.RESERVATION_CANCELLED;
            case "COMPLETED" -> NotificationType.RESERVATION_COMPLETED;
            default -> null;
        };
    }

    private String serviceNames(List<ReservationChangedMessage.Item> items) {
        String names = items.stream()
                .map(ReservationChangedMessage.Item::serviceName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        return names.isBlank() ? "el servicio reservado" : names;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String touristTitle(NotificationType type) {
        return switch (type) {
            case RESERVATION_CONFIRMED -> "Reserva confirmada";
            case RESERVATION_CANCELLED -> "Reserva cancelada";
            case RESERVATION_COMPLETED -> "Reserva completada";
        };
    }

    private String touristMessage(
            NotificationType type,
            String services,
            String date,
            int people
    ) {
        return switch (type) {
            case RESERVATION_CONFIRMED -> "Tu reserva para " + services
                    + " quedó confirmada para " + date + " (" + people + " personas).";
            case RESERVATION_CANCELLED -> "Tu reserva para " + services
                    + " del " + date + " fue cancelada.";
            case RESERVATION_COMPLETED -> "Tu reserva para " + services
                    + " del " + date + " fue completada.";
        };
    }

    private String providerTitle(NotificationType type) {
        return switch (type) {
            case RESERVATION_CONFIRMED -> "Nueva reserva confirmada";
            case RESERVATION_CANCELLED -> "Reserva cancelada";
            case RESERVATION_COMPLETED -> "Reserva completada";
        };
    }

    private String providerMessage(
            NotificationType type,
            String services,
            String date,
            int people
    ) {
        return switch (type) {
            case RESERVATION_CONFIRMED -> "Recibiste una reserva de " + people
                    + " personas para " + services + " el " + date + ".";
            case RESERVATION_CANCELLED -> "La reserva de " + people
                    + " personas para " + services + " del " + date + " fue cancelada.";
            case RESERVATION_COMPLETED -> "La reserva de " + people
                    + " personas para " + services + " del " + date + " fue completada.";
        };
    }
}
