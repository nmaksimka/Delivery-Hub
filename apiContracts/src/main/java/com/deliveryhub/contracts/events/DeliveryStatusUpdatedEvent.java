package com.deliveryhub.contracts.events;

import com.deliveryhub.contracts.enums.DeliveryStatus;

import java.time.Instant;

public record DeliveryStatusUpdatedEvent(
        Long deliveryId,
        Long orderId,
        DeliveryStatus status,
        Instant updatedAt) {
}
