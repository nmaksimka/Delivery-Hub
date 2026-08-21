package com.deliveryhub.delivery.exception;

import com.deliveryhub.delivery.entity.CourierStatus;

public class CourierUnavailableException extends RuntimeException {

    public CourierUnavailableException(Long courierId, CourierStatus status) {
        super("Courier " + courierId + " is " + status + " and cannot take a delivery");
    }
}
