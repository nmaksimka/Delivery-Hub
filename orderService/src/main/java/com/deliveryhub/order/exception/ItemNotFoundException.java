package com.deliveryhub.order.exception;

import jakarta.persistence.EntityNotFoundException;

public class ItemNotFoundException extends EntityNotFoundException implements Coded {

    private final ErrorCode code;

    public ItemNotFoundException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    @Override
    public ErrorCode code() {
        return code;
    }
}
