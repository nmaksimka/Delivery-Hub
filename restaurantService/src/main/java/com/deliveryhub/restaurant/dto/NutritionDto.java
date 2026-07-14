package com.deliveryhub.restaurant.dto;

import java.math.BigDecimal;

public record NutritionDto(
        BigDecimal energyKcal,
        BigDecimal proteinGrams,
        BigDecimal fatGrams,
        BigDecimal carbsGrams,
        String nutriScore) {
}
