package com.deliveryhub.contracts.events;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryStatusUpdatedEvent {
    private Long deliveryId;
    private Long orderId;
    private DeliveryStatus status;
    private Instant updatedAt;
}
