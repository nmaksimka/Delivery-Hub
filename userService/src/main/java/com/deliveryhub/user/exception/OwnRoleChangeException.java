package com.deliveryhub.user.exception;

public class OwnRoleChangeException extends RuntimeException {

    public OwnRoleChangeException() {
        super("Administrator cannot change own role");
    }
}
