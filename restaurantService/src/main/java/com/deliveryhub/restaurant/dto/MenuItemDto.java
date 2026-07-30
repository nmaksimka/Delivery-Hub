package com.deliveryhub.restaurant.dto;

import com.deliveryhub.restaurant.entity.MenuSection;

import java.math.BigDecimal;

public record MenuItemDto(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String category,
        boolean available,
        String imageUrl,
        MenuSection section,
        NutritionDto nutrition,
        Long restaurantId) {
}
