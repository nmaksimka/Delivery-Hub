package com.deliveryhub.restaurant.importer;

import com.deliveryhub.restaurant.entity.MenuSection;
import com.deliveryhub.restaurant.entity.NutriScore;
import com.deliveryhub.restaurant.entity.Nutrition;
import com.deliveryhub.restaurant.foodfacts.FoodFactsProduct;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductNutritionTest {

    @Test
    void roundsValuesToOneDecimalAndReadsNutriScoreCaseInsensitively() {
        FoodFactsProduct product = product("B", new FoodFactsProduct.Nutriments(52.34, 1.25, 0.0, 11.96));

        Nutrition nutrition = ProductNutrition.of(product);

        assertThat(nutrition.energyKcal()).isEqualByComparingTo("52.3");
        assertThat(nutrition.proteinGrams()).isEqualByComparingTo("1.3");
        assertThat(nutrition.fatGrams()).isEqualByComparingTo("0.0");
        assertThat(nutrition.carbsGrams()).isEqualByComparingTo("12.0");
        assertThat(nutrition.nutriScore()).isEqualTo(NutriScore.B);
    }

    @Test
    void productWithoutNutrimentsStillKeepsNutriScore() {
        Nutrition nutrition = ProductNutrition.of(product("a", null));

        assertThat(nutrition.energyKcal()).isNull();
        assertThat(nutrition.nutriScore()).isEqualTo(NutriScore.A);
        assertThat(nutrition.isEmpty()).isFalse();
    }

    @Test
    void absurdOrNegativeValuesAreDropped() {
        Nutrition nutrition = ProductNutrition.of(
                product(null, new FoodFactsProduct.Nutriments(99_999.0, -4.0, Double.NaN, 10.0)));

        assertThat(nutrition.energyKcal()).isNull();
        assertThat(nutrition.proteinGrams()).isNull();
        assertThat(nutrition.fatGrams()).isNull();
        assertThat(nutrition.carbsGrams()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void nothingKnownGivesEmptyNutrition() {
        assertThat(ProductNutrition.of(product("unknown", null)).isEmpty()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "f", "ab", "not-applicable"})
    void unsupportedGradesAreNotParsed(String grade) {
        assertThat(NutriScore.parse(grade)).isEmpty();
    }

    @Test
    void priceDependsOnSectionAndIsStable() {
        assertThat(ProductPricing.priceOf("3017620422003", MenuSection.PRODUCT))
                .isEqualTo(ProductPricing.priceOf("3017620422003", MenuSection.PRODUCT));
        for (int code = 100; code < 300; code++) {
            assertThat(ProductPricing.priceOf(Integer.toString(code), MenuSection.DRINK))
                    .isBetween(new BigDecimal("70.00"), new BigDecimal("165.00"));
            assertThat(ProductPricing.priceOf(Integer.toString(code), MenuSection.PRODUCT))
                    .isBetween(new BigDecimal("90.00"), new BigDecimal("185.00"));
        }
    }

    @Test
    void everyShelfGroupBelongsToKnownShelf() {
        assertThat(ProductShelves.groups())
                .allSatisfy(group -> assertThat(group.shelf()).isIn(ProductShelves.GROCERY, ProductShelves.BAR));
        assertThat(ProductShelves.GROCERY.section()).isEqualTo(MenuSection.PRODUCT);
        assertThat(ProductShelves.BAR.section()).isEqualTo(MenuSection.DRINK);
    }

    private static FoodFactsProduct product(String grade, FoodFactsProduct.Nutriments nutriments) {
        return new FoodFactsProduct("1", "Name", null, null, null, null, grade, nutriments);
    }
}
