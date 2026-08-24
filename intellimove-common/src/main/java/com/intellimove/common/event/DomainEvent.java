package com.intellimove.common.event;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Base domain event published via Kafka.
 * All concrete events extend this class.
 */
@Data
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@SuperBuilder(toBuilder = true)
public abstract class DomainEvent {

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    private String eventType;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private String correlationId;

    private String sagaId;

    private String source;

    @Builder.Default
    private Map<String, String> metadata = Map.of();

    private Long version;
}
