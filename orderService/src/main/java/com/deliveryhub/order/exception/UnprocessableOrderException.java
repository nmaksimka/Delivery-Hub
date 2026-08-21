package com.deliveryhub.order.exception;

public class UnprocessableOrderException extends RuntimeException implements Coded {

    private final ErrorCode code;

    public UnprocessableOrderException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    @Override
    public ErrorCode code() {
        return code;
    }
}
