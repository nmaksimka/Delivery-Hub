package com.deliveryhub.order.idempotency;

import com.deliveryhub.order.exception.Coded;
import com.deliveryhub.order.exception.ErrorCode;

public class MissingIdempotencyKeyException extends RuntimeException implements Coded {

    public MissingIdempotencyKeyException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.IDEMPOTENCY_KEY_REQUIRED;
    }
}
