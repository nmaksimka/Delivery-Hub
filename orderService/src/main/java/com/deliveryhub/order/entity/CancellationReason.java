package com.deliveryhub.order.entity;

public enum CancellationReason {
    CUSTOMER,
    ADMIN,
    PAYMENT_REJECTED,
    DELIVERY_REJECTED,
    DELIVERY_CANCELLED,
    PAYMENT_TIMEOUT,
    COURIER_TIMEOUT,
    CONFIRMATION_TIMEOUT,
    UNKNOWN
}
