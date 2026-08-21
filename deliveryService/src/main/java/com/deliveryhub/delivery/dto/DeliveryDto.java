package com.deliveryhub.delivery.dto;

import com.deliveryhub.contracts.enums.DeliveryStatus;

import java.time.Instant;

public record DeliveryDto(
        Long id,
        Long orderId,
        Long userId,
        Long courierId,
        DeliveryStatus status,
        Instant estimatedDeliveryTime,
        Instant createdAt,
        Instant updatedAt,
        String address,
        String comment) {

    public DeliveryDto withoutAddress() {
        return new DeliveryDto(id, orderId, userId, courierId, status, estimatedDeliveryTime, createdAt, updatedAt,
                null, null);
    }
}
