package com.deliveryhub.contracts;

public final class Topics {

    public static final String ORDER_CREATED = "order-created";
    public static final String ORDER_CANCELLED = "order-cancelled";
    public static final String DELIVERY_STATUS_UPDATED = "delivery-status-updated";
    public static final String COURIER_ASSIGNED = "courier-assigned";
    public static final String DELIVERY_REJECTED = "delivery-rejected";
    public static final String PAYMENT_HELD = "payment-held";
    public static final String PAYMENT_REJECTED = "payment-rejected";
    public static final String PAYMENT_CAPTURED = "payment-captured";
    public static final String PAYMENT_RELEASED = "payment-released";
    public static final String PAYMENT_REFUNDED = "payment-refunded";
    public static final String RESTAURANT_CATALOG_CHANGED = "restaurant-catalog-changed";
    public static final String REVIEW_SUBMITTED = "review-submitted";

    public static final String DEAD_LETTER_SUFFIX = ".DLT";

    private Topics() {
    }

    public static String deadLetterOf(String topic) {
        return topic + DEAD_LETTER_SUFFIX;
    }
}
