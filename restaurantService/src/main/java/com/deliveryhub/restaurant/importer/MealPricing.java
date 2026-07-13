package com.deliveryhub.restaurant.importer;

import java.math.BigDecimal;
import java.util.Map;

public final class MealPricing {

    private static final int DEFAULT_BASE = 400;
    private static final int STEP = 10;
    private static final int STEPS = 12;

    private static final Map<String, Integer> BASE_BY_CATEGORY = Map.ofEntries(
            Map.entry("Beef", 590),
            Map.entry("Breakfast", 330),
            Map.entry("Chicken", 450),
            Map.entry("Dessert", 290),
            Map.entry("Goat", 640),
            Map.entry("Lamb", 690),
            Map.entry("Miscellaneous", 390),
            Map.entry("Pasta", 470),
            Map.entry("Pork", 520),
            Map.entry("Seafood", 650),
            Map.entry("Side", 220),
            Map.entry("Starter", 310),
            Map.entry("Vegan", 380),
            Map.entry("Vegetarian", 360));

    private MealPricing() {
    }

    public static BigDecimal priceOf(String mealId, String category) {
        int base = BASE_BY_CATEGORY.getOrDefault(category == null ? "" : category, DEFAULT_BASE);
        int variation = Math.floorMod(mealId.hashCode(), STEPS) * STEP;
        return BigDecimal.valueOf(base + variation).setScale(2);
    }
}
