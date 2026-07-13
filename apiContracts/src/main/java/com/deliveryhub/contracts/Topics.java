package com.deliveryhub.contracts;

public final class Topics {

    public static final String ORDER_CREATED = "order-created";
    public static final String ORDER_CANCELLED = "order-cancelled";
    public static final String DELIVERY_STATUS_UPDATED = "delivery-status-updated";

    public static final String DEAD_LETTER_SUFFIX = ".DLT";

    private Topics() {
    }

    public static String deadLetterOf(String topic) {
        return topic + DEAD_LETTER_SUFFIX;
    }
}
