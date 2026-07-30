package com.deliveryhub.user.exception;

public class UserNotFoundException extends RuntimeException {

    private UserNotFoundException(String message) {
        super(message);
    }

    public static UserNotFoundException byId(Long id) {
        return new UserNotFoundException("User with ID: " + id + " was not found");
    }

    public static UserNotFoundException byEmail(String email) {
        return new UserNotFoundException("User with email " + email + " was not found");
    }
}
