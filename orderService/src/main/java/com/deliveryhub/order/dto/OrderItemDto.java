package com.deliveryhub.order.dto;

import java.math.BigDecimal;

public record OrderItemDto(
        Long id,
        Long menuItemId,
        String menuItemName,
        Integer quantity,
        BigDecimal pricePerUnit) {
}
