package com.deliveryhub.contracts.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        Long orderId,
        Long userId,
        Long restaurantId,
        BigDecimal totalAmount,
        Instant createdAt,
        List<OrderItemPayload> items) implements IntegrationEvent {

    public record OrderItemPayload(
            Long menuItemId,
            String itemName,
            Integer quantity,
            BigDecimal pricePerUnit) {
    }
}
