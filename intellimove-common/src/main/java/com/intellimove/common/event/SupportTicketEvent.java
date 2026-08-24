package com.intellimove.common.event;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketEvent extends DomainEvent {

    private String ticketId;
    private String customerId;
    private String rideId;
    private String issueDescription;
    private String priority;
    private String status;
}
