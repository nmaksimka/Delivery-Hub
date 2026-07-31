package com.deliveryhub.order.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartDto(
        CartRestaurantDto restaurant,
        List<CartLineDto> items,
        BigDecimal total,
        boolean orderable) {

    public static CartDto empty() {
        return new CartDto(null, List.of(), BigDecimal.ZERO, false);
    }
}
