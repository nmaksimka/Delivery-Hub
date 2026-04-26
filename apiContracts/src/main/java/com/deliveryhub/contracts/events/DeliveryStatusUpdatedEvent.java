package com.deliveryhub.contracts.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryStatusUpdatedEvent {
    private Long deliveryId;
    private Long orderId;
    private String status;
    private LocalDateTime updatedAt;
}