package com.deliveryhub.order.exception;

public class OrderRejectedException extends RuntimeException implements Coded {

    private final ErrorCode code;

    public OrderRejectedException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    @Override
    public ErrorCode code() {
        return code;
    }
}
