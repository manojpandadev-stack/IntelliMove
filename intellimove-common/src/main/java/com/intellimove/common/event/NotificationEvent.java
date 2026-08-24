package com.intellimove.common.event;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent extends DomainEvent {

    private String recipientId;
    private String recipientType;
    private String notificationType;
    private String channel;
    private String title;
    private String message;
    private Map<String, String> data;
}
