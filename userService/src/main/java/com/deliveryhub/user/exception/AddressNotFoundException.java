package com.deliveryhub.user.exception;

public class AddressNotFoundException extends RuntimeException {

    public AddressNotFoundException(Long id) {
        super("Address with ID: " + id + " was not found");
    }
}
