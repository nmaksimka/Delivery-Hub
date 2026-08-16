package com.deliveryhub.user.exception;

public class AddressNotOnMapException extends RuntimeException {

    public AddressNotOnMapException(String street, String house) {
        super("Address " + street.strip() + ", " + house.strip() + " was not found on the map of Томск");
    }
}
