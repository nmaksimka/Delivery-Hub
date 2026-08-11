package com.deliveryhub.order.entity;

import java.util.Set;

public enum OrderStatus {
    CREATED,
    PAYMENT_HELD,
    COURIER_ASSIGNED,
    CONFIRMED,
    IN_DELIVERY,
    DELIVERED,
    CANCELLED;

    public boolean isFinal() {
        return this == DELIVERED || this == CANCELLED;
    }

    public boolean isAwaitingSaga() {
        return this == CREATED || this == PAYMENT_HELD || this == COURIER_ASSIGNED;
    }

    public boolean canChangeTo(OrderStatus next) {
        return nextSteps().contains(next);
    }

    public boolean isBefore(OrderStatus other) {
        return ordinal() < other.ordinal();
    }

    private Set<OrderStatus> nextSteps() {
        return switch (this) {
            case CREATED, PAYMENT_HELD, COURIER_ASSIGNED -> Set.of(CANCELLED);
            case CONFIRMED -> Set.of(IN_DELIVERY, CANCELLED);
            case IN_DELIVERY -> Set.of(DELIVERED, CANCELLED);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }
}
