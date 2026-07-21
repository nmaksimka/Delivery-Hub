package com.deliveryhub.restaurant.security;

public class OwnerOnlyException extends RuntimeException {

    public OwnerOnlyException() {
        super("Cabinet is available only to restaurant owners");
    }
}
