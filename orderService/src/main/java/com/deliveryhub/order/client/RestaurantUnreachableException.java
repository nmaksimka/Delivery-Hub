package com.deliveryhub.order.client;

public class RestaurantUnreachableException extends RestaurantServiceUnavailableException {

    public RestaurantUnreachableException(Throwable cause) {
        super("Restaurant service is unreachable", cause);
    }
}
