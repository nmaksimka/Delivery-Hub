package com.deliveryhub.user.exception;

public class AddressLimitReachedException extends RuntimeException {

    public AddressLimitReachedException(int limit) {
        super("A user can have at most " + limit + " addresses");
    }
}
