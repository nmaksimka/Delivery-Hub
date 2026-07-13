package com.deliveryhub.restaurant.mealdb;

public class MealDbUnavailableException extends RuntimeException {

    public MealDbUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
