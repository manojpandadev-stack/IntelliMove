package com.intellimove.user.service;

import com.intellimove.common.event.DomainEvent;
import com.intellimove.common.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes USER_REGISTERED events (auth-service, via its transactional outbox)
 * and provisions the matching user profile so GET /api/v1/users/{id} succeeds
 * for freshly registered users.
 *
 * Provisioning is idempotent per userId (and guarded against email collisions),
 * so duplicate deliveries, outbox retries or Kafka replays cannot create
 * duplicate users. Unexpected failures propagate so Kafka redelivers with the
 * configured backoff; malformed/unroutable events are logged and acknowledged.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserProvisioningConsumer {

    private final UserService userService;

    @KafkaListener(topics = "user-events", groupId = "user-service")
    public void handleUserRegistered(DomainEvent event) {
        if (!(event instanceof UserRegisteredEvent registered)) {
            log.debug("Ignoring non-registration event on user-events: {}", event.getEventType());
            return;
        }
        if (registered.getUserId() == null || registered.getUserId().isBlank()) {
            log.warn("UserRegisteredEvent without userId, skipping (eventId={})", event.getEventId());
            return;
        }
        userService.provisionUser(registered);
    }
}