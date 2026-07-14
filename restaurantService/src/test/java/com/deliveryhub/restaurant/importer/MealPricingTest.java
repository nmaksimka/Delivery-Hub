package com.deliveryhub.restaurant.importer;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MealPricingTest {

    @Test
    void sameMealAlwaysCostsTheSame() {
        assertThat(MealPricing.priceOf("52772", "Chicken")).isEqualTo(MealPricing.priceOf("52772", "Chicken"));
    }

    @Test
    void priceStaysInRangeOfItsCategoryAndIsRoundToTenRoubles() {
        for (int id = 52_700; id < 52_800; id++) {
            BigDecimal price = MealPricing.priceOf(Integer.toString(id), "Dessert");

            assertThat(price).isBetween(new BigDecimal("290.00"), new BigDecimal("400.00"));
            assertThat(price.remainder(BigDecimal.TEN)).isEqualByComparingTo("0");
            assertThat(price.scale()).isEqualTo(2);
        }
    }

    @Test
    void unknownCategoryGetsDefaultBase() {
        assertThat(MealPricing.priceOf("1", "Insects")).isBetween(new BigDecimal("400.00"), new BigDecimal("510.00"));
        assertThat(MealPricing.priceOf("1", null)).isBetween(new BigDecimal("400.00"), new BigDecimal("510.00"));
    }

    @Test
    void lambIsMoreExpensiveThanSideDish() {
        assertThat(MealPricing.priceOf("7", "Lamb")).isGreaterThan(MealPricing.priceOf("7", "Side"));
    }
}
