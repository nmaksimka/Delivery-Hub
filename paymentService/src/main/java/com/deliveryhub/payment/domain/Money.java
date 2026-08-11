package com.deliveryhub.payment.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final int SCALE = 2;
    private static final BigDecimal MAX = new BigDecimal("9999999999.99");

    private Money() {
    }

    public static BigDecimal positive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new PaymentException(ErrorCode.INVALID_AMOUNT, "Amount must be positive");
        }
        return normalized(amount);
    }

    public static BigDecimal nonNegative(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new PaymentException(ErrorCode.INVALID_AMOUNT, "Amount must not be negative");
        }
        return normalized(amount);
    }

    private static BigDecimal normalized(BigDecimal amount) {
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new PaymentException(ErrorCode.INVALID_AMOUNT, "Amount must have at most two decimal places");
        }
        if (amount.compareTo(MAX) > 0) {
            throw new PaymentException(ErrorCode.INVALID_AMOUNT, "Amount is too large");
        }
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE);
    }
}
