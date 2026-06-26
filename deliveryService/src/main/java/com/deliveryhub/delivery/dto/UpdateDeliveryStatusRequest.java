package com.deliveryhub.delivery.dto;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateDeliveryStatusRequest(
        @NotNull(message = "Status is required") DeliveryStatus status,
        Long courierId) {
}
