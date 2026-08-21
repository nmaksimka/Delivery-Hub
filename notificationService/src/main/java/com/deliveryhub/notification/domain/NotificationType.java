package com.deliveryhub.notification.domain;

public enum NotificationType {
    ORDER_CREATED("order-created"),
    PAYMENT_REJECTED("payment-rejected"),
    DELIVERY_REJECTED("delivery-rejected"),
    COURIER_ASSIGNED("courier-assigned"),
    ORDER_DELIVERED("order-delivered"),
    ORDER_CANCELLED("order-cancelled");

    private final String template;

    NotificationType(String template) {
        this.template = template;
    }

    public String template() {
        return template;
    }
}
