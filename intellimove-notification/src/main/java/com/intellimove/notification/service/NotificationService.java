package com.intellimove.notification.service;

import com.intellimove.common.event.*;
import com.intellimove.notification.channel.NotificationChannel;
import com.intellimove.notification.entity.Notification;
import com.intellimove.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final List<NotificationChannel> channels;

    public NotificationService(NotificationRepository notificationRepository,
                               List<NotificationChannel> channels) {
        this.notificationRepository = notificationRepository;
        this.channels = channels;
    }

    /**
     * Listen to ride-events and create notifications for ride lifecycle changes.
     * This bridges the ride event flow to the notification pipeline.
     */
    @KafkaListener(topics = "ride-events", groupId = "notification-service")
    public void handleRideEvent(DomainEvent event) {
        log.info("Received ride event: {} for ride: {}", event.getEventType(), event.getCorrelationId());

        String notificationType = event.getEventType();
        String recipientId = null;
        String title = "";
        String message = "";

        try {
            String eventType = event.getEventType();
            // Map by eventType string for flexibility — event classes may be reused
            if (event instanceof RideCompletedEvent completed) {
                recipientId = completed.getCustomerId();
                title = "Ride Completed";
                message = String.format("Your ride has been completed. Fare: %s %s",
                        completed.getCurrency(), completed.getFareAmount());
            } else if (event instanceof DriverAssignedEvent assigned) {
                recipientId = assigned.getCustomerId();
                title = "Driver Assigned";
                message = "A driver has been assigned to your ride.";
            } else if ("DRIVER_ACCEPTED".equals(eventType) && event instanceof DriverAssignedEvent da) {
                recipientId = da.getCustomerId();
                title = "Driver Accepted";
                message = "Your driver has accepted the ride.";
            } else if ("RIDE_STARTED".equals(eventType) && event instanceof RideRequestedEvent rs) {
                recipientId = rs.getCustomerId();
                title = "Trip Started";
                message = "Your trip has started.";
            } else if (event instanceof RideCancelledEvent cancelled) {
                recipientId = cancelled.getCustomerId();
                title = "Ride Cancelled";
                message = String.format("Ride cancelled. Reason: %s", cancelled.getCancellationReason());
            } else if (event instanceof RideRequestedEvent requested) {
                recipientId = requested.getCustomerId();
                title = "Ride Requested";
                message = "Your ride request has been received.";
            } else {
                log.debug("Unhandled ride event type: {}", event.getEventType());
                return;
            }

            if (recipientId == null) {
                log.warn("No recipient for ride event: {}", event.getEventType());
                return;
            }

            Notification notification = Notification.builder()
                    .recipientId(recipientId)
                    .recipientType("CUSTOMER")
                    .notificationType(notificationType)
                    .channel("IN_APP")
                    .title(title)
                    .message(message)
                    .build();

            try {
                sendNotification(notification, Map.of());
                notification.setSent(true);
                notification.setSentAt(Instant.now());
            } catch (Exception e) {
                log.error("Failed to send ride notification: {}", e.getMessage());
                notification.setErrorMessage(e.getMessage());
            }

            notificationRepository.save(notification);
        } catch (Exception e) {
            log.error("Error processing ride event {}: {}", event.getEventType(), e.getMessage());
        }
    }

    @KafkaListener(topics = "notification-events", groupId = "notification-service")
    public void handleNotificationEvent(NotificationEvent event) {
        log.info("Received notification event: {} for recipient: {}",
                event.getNotificationType(), event.getRecipientId());

        Notification notification = Notification.builder()
                .recipientId(event.getRecipientId())
                .recipientType(event.getRecipientType())
                .notificationType(event.getNotificationType())
                .channel(event.getChannel())
                .title(event.getTitle())
                .message(event.getMessage())
                .build();

        try {
            sendNotification(notification, event.getData());
            notification.setSent(true);
            notification.setSentAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed to send notification: {}", e.getMessage());
            notification.setErrorMessage(e.getMessage());
        }

        notificationRepository.save(notification);
    }

    @Async
    public void sendNotification(Notification notification, Map<String, String> data) {
        String channelType = notification.getChannel();
        for (NotificationChannel channel : channels) {
            if (channelType == null || channelType.isEmpty()
                    || channel.getChannelType().equalsIgnoreCase(channelType)
                    || "IN_APP".equals(channel.getChannelType())) {
                try {
                    channel.send(notification.getRecipientId(),
                            notification.getTitle(), notification.getMessage(), data);
                } catch (Exception e) {
                    log.error("Channel {} failed: {}", channel.getChannelType(), e.getMessage());
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> getRecipientNotifications(String recipientId, int page, int size) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(
                recipientId,
                org.springframework.data.domain.PageRequest.of(page, size)).getContent();
    }

    @Transactional(readOnly = true)
    public Page<Notification> getRecipientNotificationsPage(String recipientId,
                                                            org.springframework.data.domain.Pageable pageable) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String recipientId) {
        return notificationRepository.countByRecipientIdAndReadFalse(recipientId);
    }

    @Transactional
    public void markAsRead(UUID notificationId, String recipientId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getRecipientId().equals(recipientId)) {
                n.setRead(true);
                n.setReadAt(Instant.now());
                notificationRepository.save(n);
            }
        });
    }

    /**
     * Marks every unread notification belonging to the given recipient as read.
     * Only notifications owned by the recipient are touched (IDOR-safe).
     */
    @Transactional
    public int markAllAsRead(String recipientId) {
        List<Notification> unread = notificationRepository.findByRecipientIdAndReadFalse(recipientId);
        Instant now = Instant.now();
        for (Notification n : unread) {
            n.setRead(true);
            n.setReadAt(now);
        }
        notificationRepository.saveAll(unread);
        return unread.size();
    }
}
