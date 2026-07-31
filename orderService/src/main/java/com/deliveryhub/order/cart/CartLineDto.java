package com.deliveryhub.order.cart;

import java.math.BigDecimal;

public record CartLineDto(
        Long menuItemId,
        String name,
        BigDecimal price,
        int quantity,
        BigDecimal lineTotal,
        boolean available) {
}
