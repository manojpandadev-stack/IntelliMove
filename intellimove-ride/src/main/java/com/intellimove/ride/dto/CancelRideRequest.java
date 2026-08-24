package com.intellimove.ride.dto;

import com.intellimove.common.enums.CancellationReason;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelRideRequest {

    @NotNull(message = "Cancellation reason is required")
    private CancellationReason reason;

    private String note;
}
