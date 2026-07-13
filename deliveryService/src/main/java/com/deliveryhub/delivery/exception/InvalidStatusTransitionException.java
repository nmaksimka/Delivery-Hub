package com.deliveryhub.delivery.exception;

import com.deliveryhub.contracts.enums.DeliveryStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(Long deliveryId, DeliveryStatus from, DeliveryStatus to) {
        super("Delivery " + deliveryId + " cannot change status from " + from + " to " + to);
    }
}
