package com.deliveryhub.delivery.exception;

public class CourierAlreadyRegisteredException extends RuntimeException {

    public CourierAlreadyRegisteredException(Long userId) {
        super("User " + userId + " is already registered as a courier");
    }
}
