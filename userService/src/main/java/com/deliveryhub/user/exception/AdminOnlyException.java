package com.deliveryhub.user.exception;

public class AdminOnlyException extends RuntimeException {

    public AdminOnlyException() {
        super("Only administrators can manage users");
    }
}
