package com.deliveryhub.delivery.quote;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.weather")
public record WeatherProperties(
        String baseUrl,
        double latitude,
        double longitude,
        Duration connectTimeout,
        Duration readTimeout,
        Duration cacheTtl,
        double frostBelowCelsius) {
}
