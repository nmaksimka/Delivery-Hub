package com.deliveryhub.delivery.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateDeliveryRequest(
        @NotNull(message = "Order ID is required") Long orderId,
        Long userId,
        Long courierId,
        Instant estimatedDeliveryTime) {
}
