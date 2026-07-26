package com.deliveryhub.order.client;

public class InvalidRestaurantRequestException extends RuntimeException {

    public InvalidRestaurantRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
