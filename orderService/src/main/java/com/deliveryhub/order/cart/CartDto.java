package com.deliveryhub.order.cart;

import com.deliveryhub.order.exception.ErrorCode;

import java.math.BigDecimal;
import java.util.List;

public record CartDto(
        CartRestaurantDto restaurant,
        List<CartLineDto> items,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        String promoCode,
        ErrorCode promoProblem,
        boolean orderable) {

    public static CartDto empty() {
        return new CartDto(null, List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, false);
    }
}
