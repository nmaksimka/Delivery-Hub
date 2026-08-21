package com.deliveryhub.delivery.exception;

public class CourierBusyException extends RuntimeException {

    public CourierBusyException(Long courierId) {
        super("Courier " + courierId + " is on a delivery, status can change after it is finished");
    }
}
