package com.deliveryhub.contracts.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        Long userId,
        Long restaurantId,
        BigDecimal totalAmount,
        Instant createdAt,
        List<OrderItemPayload> items) {

    public record OrderItemPayload(
            Long menuItemId,
            String itemName,
            Integer quantity,
            BigDecimal pricePerUnit) {
    }
}
