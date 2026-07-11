package com.deliveryhub.delivery.exception;

public class DeliveryAlreadyExistsException extends RuntimeException {

    public DeliveryAlreadyExistsException(Long orderId) {
        super("Delivery already exists for order ID: " + orderId);
    }
}
