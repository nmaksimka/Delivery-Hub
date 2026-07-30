package com.deliveryhub.restaurant.dto;

import com.deliveryhub.restaurant.entity.MenuSection;

import java.math.BigDecimal;

public record MenuItemView(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String category,
        boolean available,
        String externalImageUrl,
        String imageKey,
        MenuSection section,
        NutritionDto nutrition,
        Long restaurantId) {
}
