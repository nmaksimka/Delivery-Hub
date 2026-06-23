package com.deliveryhub.restaurant.dto;

import java.math.BigDecimal;

public record MenuItemDto(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String category,
        boolean available,
        Long restaurantId) {
}
