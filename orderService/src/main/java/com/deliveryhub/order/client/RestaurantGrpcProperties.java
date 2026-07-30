package com.deliveryhub.order.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.restaurant-grpc")
public record RestaurantGrpcProperties(Duration deadline) {

    public RestaurantGrpcProperties {
        if (deadline == null || deadline.isZero() || deadline.isNegative()) {
            deadline = Duration.ofSeconds(2);
        }
    }
}
