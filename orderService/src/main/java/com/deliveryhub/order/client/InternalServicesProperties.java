package com.deliveryhub.order.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.internal-services")
public record InternalServicesProperties(
        String usersBaseUrl,
        String deliveryBaseUrl,
        String restaurantBaseUrl,
        Duration connectTimeout,
        Duration readTimeout) {
}
