package com.smxplore.sitesandservices.application.notification;

import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.model.notification.NotificationType;
import com.smxplore.sitesandservices.domain.ports.out.NotificationDeliveryPort;
import com.smxplore.sitesandservices.domain.ports.out.NotificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RequiredArgsConstructor
public class NotificationCreateService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final NotificationRepositoryPort notificationRepository;
    private final List<NotificationDeliveryPort> notificationDeliveries;
    private final Clock clock;

    /**
     * Compatibilidad con los llamadores que solo registran un canal (por ejemplo, tests
     * antiguos). En producción Spring inyecta la lista completa de canales.
     */
    public NotificationCreateService(
            NotificationRepositoryPort notificationRepository,
            NotificationDeliveryPort notificationDelivery,
            Clock clock
    ) {
        this(notificationRepository, List.of(notificationDelivery), clock);
    }

    public Mono<Void> handle(ReservationChangedMessage message) {
        NotificationType type = typeFor(message.status());
        if (type == null || message.items().isEmpty()) {
            return Mono.empty();
        }

        String items = itemNames(message.items());
        int people = message.items().stream().mapToInt(ReservationChangedMessage.Item::people).sum();
        String date = message.bookedFor() == null
                ? "la fecha indicada"
                : DATE_FORMAT.format(message.bookedFor());

        List<Notification> notifications = new ArrayList<>();
        notifications.add(notification(
                message,
                message.touristId(),
                UserRole.TOURIST,
                type,
                touristTitle(type),
                touristMessage(type, items, date, people)
        ));

        for (Recipient recipient : recipients(message.items())) {
            notifications.add(notification(
                    message,
                    recipient.id(),
                    recipient.role(),
                    type,
                    staffTitle(type),
                    staffMessage(type, items, date, people)
            ));
        }

        return Flux.fromIterable(notifications)
                .concatMap(notification -> notificationRepository.saveIfAbsent(notification)
                        .flatMap(inserted -> inserted
                                ? delivery(notification)
                                : Mono.empty()))
                .then();
    }

    private Set<Recipient> recipients(List<ReservationChangedMessage.Item> items) {
        Set<Recipient> recipients = new LinkedHashSet<>();
        for (ReservationChangedMessage.Item item : items) {
            switch (item.itemType()) {
                case "SERVICE" -> addRecipient(recipients, item.providerId(), UserRole.SERVICE_PROVIDER);
                case "ACTIVITY" -> addRecipient(recipients, item.guideId(), UserRole.GUIDE);
                case "EVENT" -> addRecipient(recipients, item.managerId(), UserRole.DESTINATION_MANAGER);
                default -> {
                    // The decoder rejects unknown item types; keep the switch exhaustive for safety.
                }
            }
        }
        return recipients;
    }

    private static void addRecipient(Set<Recipient> recipients, UUID id, UserRole role) {
        if (id != null) {
            recipients.add(new Recipient(id, role));
        }
    }

    private Mono<Void> delivery(Notification notification) {
        return Flux.fromIterable(notificationDeliveries)
                .concatMap(channel -> channel.deliver(notification)
                        .onErrorResume(exception -> {
                            log.warn(
                                    "Notification {} for recipient {} was stored in the inbox but not delivered: {}",
                                    notification.notificationId(),
                                    notification.recipientId(),
                                    exception.getMessage());
                            return Mono.empty();
                        }))
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
                message.eventId() + "|" + recipientId + "|" + recipientRole + "|" + type.name()
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
            case "NO_SHOW" -> NotificationType.RESERVATION_NO_SHOW;
            default -> null;
        };
    }

    private String itemNames(List<ReservationChangedMessage.Item> items) {
        String names = items.stream()
                .map(this::itemName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        return names.isBlank() ? "la reserva" : names;
    }

    private String itemName(ReservationChangedMessage.Item item) {
        if (item.itemName() != null && !item.itemName().isBlank()) {
            return item.itemName();
        }
        if (item.serviceName() != null && !item.serviceName().isBlank()) {
            return item.serviceName();
        }
        return switch (item.itemType()) {
            case "ACTIVITY" -> "la actividad reservada";
            case "EVENT" -> "el evento reservado";
            case "SERVICE" -> "el servicio reservado";
            default -> "la reserva";
        };
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String touristTitle(NotificationType type) {
        return switch (type) {
            case RESERVATION_CONFIRMED -> "Reserva confirmada";
            case RESERVATION_CANCELLED -> "Reserva cancelada";
            case RESERVATION_COMPLETED -> "Reserva completada";
            case RESERVATION_NO_SHOW -> "Reserva marcada como no show";
            // Este tipo lo redacta LowCapacityAlertService (RF66); aquí no debería llegar.
            case LOW_CAPACITY_ALERT -> throw new IllegalStateException(
                    "LOW_CAPACITY_ALERT is built by LowCapacityAlertService");
        };
    }

    private String touristMessage(
            NotificationType type,
            String items,
            String date,
            int people
    ) {
        return switch (type) {
            case RESERVATION_CONFIRMED -> "Tu reserva para " + items
                    + " quedó confirmada para " + date + " (" + people + " personas).";
            case RESERVATION_CANCELLED -> "Tu reserva para " + items
                    + " del " + date + " fue cancelada.";
            case RESERVATION_COMPLETED -> "Tu reserva para " + items
                    + " del " + date + " fue completada.";
            case RESERVATION_NO_SHOW -> "Tu reserva para " + items
                    + " del " + date + " fue marcada como no show.";
            case LOW_CAPACITY_ALERT -> throw new IllegalStateException(
                    "LOW_CAPACITY_ALERT is built by LowCapacityAlertService");
        };
    }

    private String staffTitle(NotificationType type) {
        return switch (type) {
            case RESERVATION_CONFIRMED -> "Nueva reserva confirmada";
            case RESERVATION_CANCELLED -> "Reserva cancelada";
            case RESERVATION_COMPLETED -> "Reserva completada";
            case RESERVATION_NO_SHOW -> "Reserva no show";
            case LOW_CAPACITY_ALERT -> throw new IllegalStateException(
                    "LOW_CAPACITY_ALERT is built by LowCapacityAlertService");
        };
    }

    private String staffMessage(
            NotificationType type,
            String items,
            String date,
            int people
    ) {
        return switch (type) {
            case RESERVATION_CONFIRMED -> "Recibiste una reserva de " + people
                    + " personas para " + items + " el " + date + ".";
            case RESERVATION_CANCELLED -> "La reserva de " + people
                    + " personas para " + items + " del " + date + " fue cancelada.";
            case RESERVATION_COMPLETED -> "La reserva de " + people
                    + " personas para " + items + " del " + date + " fue completada.";
            case RESERVATION_NO_SHOW -> "La reserva de " + people
                    + " personas para " + items + " del " + date + " fue marcada como no show.";
            case LOW_CAPACITY_ALERT -> throw new IllegalStateException(
                    "LOW_CAPACITY_ALERT is built by LowCapacityAlertService");
        };
    }

    private record Recipient(UUID id, UserRole role) {
    }
}
