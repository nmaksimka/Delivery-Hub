package com.deliveryhub.restaurant.foodfacts;

public class FoodFactsUnavailableException extends RuntimeException {

    public FoodFactsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
