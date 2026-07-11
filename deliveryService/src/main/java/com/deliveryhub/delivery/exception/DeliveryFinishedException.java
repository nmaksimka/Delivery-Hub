package com.deliveryhub.delivery.exception;

import com.deliveryhub.contracts.enums.DeliveryStatus;

public class DeliveryFinishedException extends RuntimeException {

    public DeliveryFinishedException(Long deliveryId, DeliveryStatus status) {
        super("Delivery " + deliveryId + " is already " + status);
    }
}
