package com.deliveryhub.delivery.dto;

import com.deliveryhub.contracts.enums.DeliveryStatus;

import java.time.Instant;

public record DeliveryDto(
        Long id,
        Long orderId,
        Long courierId,
        DeliveryStatus status,
        Instant estimatedDeliveryTime,
        Instant createdAt,
        Instant updatedAt) {
}
