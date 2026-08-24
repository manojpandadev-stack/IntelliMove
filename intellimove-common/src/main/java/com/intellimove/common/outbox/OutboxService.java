package com.intellimove.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.intellimove.common.event.DomainEvent;
import com.intellimove.common.event.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transactional Outbox Pattern with embedded scheduler.
 * Uses optional dependencies - works even without Kafka or specific tables.
 */
@Service
@Slf4j
public class OutboxService {

    @Autowired(required = false)
    private OutboxRepository outboxRepository;

    @Autowired(required = false)
    private EventPublisher eventPublisher;

    private final ObjectMapper objectMapper;

    public OutboxService(@Autowired(required = false) ObjectMapper configuredMapper) {
        if (configuredMapper != null) {
            this.objectMapper = configuredMapper;
        } else {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            this.objectMapper = mapper;
        }
    }

    private static final int MAX_RETRIES = 5;

    @Transactional
    public void saveEvent(DomainEvent event, String aggregateType, String aggregateId,
                          String topic, String messageKey) {
        if (outboxRepository == null) {
            log.debug("OutboxRepository not available, event {} not persisted", event.getEventType());
            // Fallback: publish directly if eventPublisher is available
            if (eventPublisher != null) {
                eventPublisher.publish(topic, messageKey, event);
            }
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(event.getEventType())
                    .payload(payload)
                    .topic(topic)
                    .messageKey(messageKey)
                    .status(OutboxStatus.PENDING)
                    .build();
            outboxRepository.save(outboxEvent);
            log.debug("Outbox event saved: {} for aggregate {}", event.getEventType(), aggregateId);
        } catch (Exception e) {
            log.error("Failed to save outbox event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save outbox event", e);
        }
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:5000}")
    @Transactional
    public void processPendingEvents() {
        if (outboxRepository == null || eventPublisher == null) {
            return;
        }
        try {
            List<OutboxEvent> pendingEvents = outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
            for (OutboxEvent outboxEvent : pendingEvents) {
                try {
                    eventPublisher.publish(outboxEvent.getTopic(), outboxEvent.getMessageKey(),
                            objectMapper.readValue(outboxEvent.getPayload(), DomainEvent.class));
                    outboxRepository.updateStatus(outboxEvent.getId(), OutboxStatus.PROCESSED, Instant.now());
                    log.debug("Outbox event {} published and marked PROCESSED", outboxEvent.getId());
                } catch (Exception e) {
                    log.error("Failed to publish outbox event {}: {}", outboxEvent.getId(), e.getMessage());
                    if (outboxEvent.getRetryCount() >= MAX_RETRIES - 1) {
                        outboxRepository.incrementRetryAndMarkFailed(
                                outboxEvent.getId(), OutboxStatus.FAILED);
                        log.warn("Outbox event {} marked FAILED after {} retries", outboxEvent.getId(), outboxEvent.getRetryCount());
                    } else {
                        outboxRepository.incrementRetryAndMarkFailed(
                                outboxEvent.getId(), OutboxStatus.PENDING);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing outbox events: {}", e.getMessage());
        }
    }
}
