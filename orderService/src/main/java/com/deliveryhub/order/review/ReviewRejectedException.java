package com.deliveryhub.order.review;

import com.deliveryhub.order.exception.Coded;
import com.deliveryhub.order.exception.ErrorCode;

public class ReviewRejectedException extends RuntimeException implements Coded {

    private final ErrorCode code;

    public ReviewRejectedException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    @Override
    public ErrorCode code() {
        return code;
    }
}
