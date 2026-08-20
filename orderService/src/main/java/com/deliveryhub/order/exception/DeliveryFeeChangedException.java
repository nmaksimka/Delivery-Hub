package com.deliveryhub.order.exception;

import java.math.BigDecimal;

public class DeliveryFeeChangedException extends RuntimeException implements Coded {

    private final BigDecimal deliveryFee;

    public DeliveryFeeChangedException(BigDecimal expected, BigDecimal deliveryFee) {
        super("Delivery fee has grown from " + expected + " to " + deliveryFee + ", confirm the new price");
        this.deliveryFee = deliveryFee;
    }

    public BigDecimal deliveryFee() {
        return deliveryFee;
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.DELIVERY_FEE_CHANGED;
    }
}
