package com.deliveryhub.order.exception;

import com.deliveryhub.order.entity.OrderStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(Long orderId, OrderStatus from, OrderStatus to) {
        super("Order " + orderId + " cannot change status from " + from + " to " + to);
    }
}
