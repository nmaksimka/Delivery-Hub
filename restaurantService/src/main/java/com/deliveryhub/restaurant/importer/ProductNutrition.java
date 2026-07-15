package com.deliveryhub.restaurant.importer;

import com.deliveryhub.restaurant.entity.NutriScore;
import com.deliveryhub.restaurant.entity.Nutrition;
import com.deliveryhub.restaurant.foodfacts.FoodFactsProduct;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ProductNutrition {

    private static final BigDecimal MAX_PER_100G = new BigDecimal("9000");

    private ProductNutrition() {
    }

    public static Nutrition of(FoodFactsProduct product) {
        FoodFactsProduct.Nutriments nutriments = product.nutriments();
        NutriScore score = NutriScore.parse(product.nutriScoreGrade()).orElse(null);
        if (nutriments == null) {
            return new Nutrition(null, null, null, null, score);
        }
        return new Nutrition(
                grams(nutriments.energyKcal()),
                grams(nutriments.proteins()),
                grams(nutriments.fat()),
                grams(nutriments.carbohydrates()),
                score);
    }

    private static BigDecimal grams(Double value) {
        if (value == null || value.isNaN() || value.isInfinite() || value < 0) {
            return null;
        }
        BigDecimal amount = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
        return amount.compareTo(MAX_PER_100G) > 0 ? null : amount;
    }
}
