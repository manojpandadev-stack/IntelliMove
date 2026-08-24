package com.intellimove.common.event;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DriverStatusChangedEvent extends DomainEvent {

    private String driverId;
    private String previousStatus;
    private String newStatus;
}
