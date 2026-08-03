package com.deliveryhub.payment.domain;

public class PaymentException extends RuntimeException {

    private final ErrorCode code;

    public PaymentException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
