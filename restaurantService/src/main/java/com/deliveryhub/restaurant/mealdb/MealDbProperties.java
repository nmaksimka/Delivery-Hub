package com.deliveryhub.restaurant.mealdb;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.mealdb")
public record MealDbProperties(
        @DefaultValue("https://www.themealdb.com/api/json/v1/1") String baseUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout,
        @DefaultValue("5") int requestsPerSecond,
        @DefaultValue("10s") Duration rateLimitWait,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("500ms") Duration retryBackoff,
        @DefaultValue("6h") Duration cacheTtl,
        @DefaultValue("12") int mealsPerCategory) {
}
