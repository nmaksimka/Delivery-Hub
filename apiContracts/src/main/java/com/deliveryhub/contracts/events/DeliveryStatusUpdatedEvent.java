package com.deliveryhub.contracts.events;

import com.deliveryhub.contracts.enums.DeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public record DeliveryStatusUpdatedEvent(
        UUID eventId,
        Long deliveryId,
        Long orderId,
        DeliveryStatus status,
        Instant updatedAt) implements IntegrationEvent {
}
