package com.deliveryhub.restaurant.foodfacts;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FoodFactsProduct(
        String code,
        @JsonProperty("product_name") String name,
        @JsonProperty("product_name_ru") String russianName,
        String brands,
        String quantity,
        @JsonProperty("image_front_url") String imageUrl,
        @JsonProperty("nutriscore_grade") String nutriScoreGrade,
        Nutriments nutriments) {

    public record Nutriments(
            @JsonProperty("energy-kcal_100g") Double energyKcal,
            @JsonProperty("proteins_100g") Double proteins,
            @JsonProperty("fat_100g") Double fat,
            @JsonProperty("carbohydrates_100g") Double carbohydrates) {
    }

    public String displayName() {
        if (russianName != null && !russianName.isBlank()) {
            return russianName.strip();
        }
        return name == null ? null : name.strip();
    }
}
