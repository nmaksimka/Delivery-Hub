package com.deliveryhub.delivery.dto;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import jakarta.validation.constraints.AssertTrue;

public record UpdateDeliveryStatusRequest(DeliveryStatus status, Long courierId) {

    @AssertTrue(message = "Status or courier ID is required")
    public boolean isStatusOrCourierPresent() {
        return status != null || courierId != null;
    }
}
