package com.deliveryhub.order.exception;

import com.deliveryhub.order.entity.OrderStatus;

public class InvalidStatusTransitionException extends RuntimeException implements Coded {

    public InvalidStatusTransitionException(Long orderId, OrderStatus from, OrderStatus to) {
        super("Order " + orderId + " cannot change status from " + from + " to " + to);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.INVALID_STATUS_TRANSITION;
    }
}
