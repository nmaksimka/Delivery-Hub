package com.deliveryhub.delivery.exception;

public class OrderAlreadyCancelledException extends RuntimeException {

    public OrderAlreadyCancelledException(Long orderId) {
        super("Order " + orderId + " is cancelled, delivery cannot be created");
    }
}
