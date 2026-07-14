package com.deliveryhub.restaurant.foodfacts;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.foodfacts")
public record FoodFactsProperties(
        @DefaultValue("https://world.openfoodfacts.org") String baseUrl,
        @DefaultValue("DeliveryHub/1.0 (student project)") String userAgent,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("10s") Duration readTimeout,
        @DefaultValue("2") int requestsPerSecond,
        @DefaultValue("10s") Duration rateLimitWait,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("1s") Duration retryBackoff,
        @DefaultValue("12h") Duration cacheTtl,
        @DefaultValue("10") int productsPerCategory) {
}
