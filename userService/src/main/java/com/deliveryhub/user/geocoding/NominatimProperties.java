package com.deliveryhub.user.geocoding;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.geocoding")
public record NominatimProperties(
        String baseUrl,
        String userAgent,
        String city,
        String countryCodes,
        String viewbox,
        Duration connectTimeout,
        Duration readTimeout,
        int requestsPerSecond,
        Duration rateLimitWait,
        int cacheSize,
        Duration cacheTtl,
        Duration notFoundCacheTtl,
        Duration retryInterval,
        int retryBatchSize) {
}
