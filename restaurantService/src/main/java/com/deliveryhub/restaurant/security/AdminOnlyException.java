package com.deliveryhub.restaurant.security;

public class AdminOnlyException extends RuntimeException {

    public AdminOnlyException() {
        super("Only administrators can change the catalog");
    }
}
