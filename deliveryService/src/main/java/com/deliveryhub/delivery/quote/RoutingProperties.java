package com.deliveryhub.delivery.quote;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.routing")
public record RoutingProperties(
        String baseUrl,
        String profile,
        String userAgent,
        Duration connectTimeout,
        Duration readTimeout,
        double fallbackDetourFactor,
        double fallbackSpeedKmh) {

    public RoutingProperties {
        if (fallbackDetourFactor < 1 || fallbackSpeedKmh <= 0) {
            throw new IllegalArgumentException("app.routing fallback factor must be >= 1 and speed positive");
        }
    }
}
