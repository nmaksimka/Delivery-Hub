package com.deliveryhub.order.exception;

public class DependencyUnavailableException extends RuntimeException implements Coded {

    private final ErrorCode code;

    public DependencyUnavailableException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    @Override
    public ErrorCode code() {
        return code;
    }
}
