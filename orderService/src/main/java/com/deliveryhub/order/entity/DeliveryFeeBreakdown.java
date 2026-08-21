package com.deliveryhub.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.Objects;

@Embeddable
public record DeliveryFeeBreakdown(
        @Column(name = "delivery_distance_m") Integer distanceMeters,
        @Column(name = "delivery_weather_surcharge", precision = 12, scale = 2) BigDecimal weatherSurcharge,
        @Column(name = "delivery_route_source", length = 20) String routeSource) {

    public DeliveryFeeBreakdown {
        Objects.requireNonNull(distanceMeters, "distanceMeters");
        Objects.requireNonNull(weatherSurcharge, "weatherSurcharge");
        Objects.requireNonNull(routeSource, "routeSource");
        if (distanceMeters < 0 || weatherSurcharge.signum() < 0) {
            throw new IllegalArgumentException("Distance and weather surcharge must not be negative");
        }
    }
}
