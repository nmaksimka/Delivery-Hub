package com.deliveryhub.payment.application;

import com.deliveryhub.payment.domain.Payment;

import java.math.BigDecimal;

public record ProviderRequest(long orderId, long userId, BigDecimal amount, String reference) {

    public static ProviderRequest newHold(long orderId, long userId, BigDecimal amount) {
        return new ProviderRequest(orderId, userId, amount, null);
    }

    public static ProviderRequest of(Payment payment) {
        return new ProviderRequest(payment.getOrderId(), payment.getUserId(), payment.getAmount(),
                payment.getProviderReference());
    }
}
