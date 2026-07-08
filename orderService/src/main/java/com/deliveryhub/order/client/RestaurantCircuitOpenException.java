package com.deliveryhub.order.client;

public class RestaurantCircuitOpenException extends RestaurantServiceUnavailableException {

    public RestaurantCircuitOpenException(Throwable cause) {
        super("Restaurant service is failing, calls are suspended", cause);
    }
}
