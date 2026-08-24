package com.intellimove.common.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Map;

/**
 * Publishes domain events to Kafka with correlation IDs for tracing.
 * Created by KafkaProducerConfig when Kafka is configured.
 */
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final Map<String, String> TOPIC_MAP = Map.of(
            "RideRequestedEvent", "ride-events",
            "DriverAssignedEvent", "ride-events",
            "DriverAcceptedEvent", "ride-events",
            "RideCompletedEvent", "ride-events",
            "RideCancelledEvent", "ride-events",
            "PaymentEvent", "payment-events",
            "NotificationEvent", "notification-events",
            "DriverStatusChangedEvent", "driver-events",
            "SupportTicketEvent", "notification-events"
    );

    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public <T extends DomainEvent> void publish(T event) {
        String topic = TOPIC_MAP.getOrDefault(event.getClass().getSimpleName(), "domain-events");
        publish(topic, event.getCorrelationId() != null ? event.getCorrelationId() : event.getEventId(), event);
    }

    public <T extends DomainEvent> void publish(String topic, String key, T event) {
        log.info("Publishing event {} to topic {} with key {}",
                event.getEventType(), topic, key);
        Message<T> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader(KafkaHeaders.KEY, key)
                .setHeader("eventId", event.getEventId())
                .setHeader("eventType", event.getEventType())
                .build();
        kafkaTemplate.send(message);
    }
}
