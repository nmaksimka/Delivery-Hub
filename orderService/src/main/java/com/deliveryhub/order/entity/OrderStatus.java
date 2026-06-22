package com.deliveryhub.order.entity;

public enum OrderStatus {
    CREATED,
    CONFIRMED,
    IN_DELIVERY,
    DELIVERED,
    CANCELLED;

    public boolean isFinal() {
        return this == DELIVERED || this == CANCELLED;
    }

    public boolean canChangeTo(OrderStatus next) {
        if (isFinal()) {
            return false;
        }
        return next == CANCELLED || next.ordinal() > ordinal();
    }
}
