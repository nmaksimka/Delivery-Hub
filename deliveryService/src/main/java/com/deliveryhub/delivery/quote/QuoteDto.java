package com.deliveryhub.delivery.quote;

import java.math.BigDecimal;

public record QuoteDto(
        int distanceMeters,
        int etaMinutes,
        BigDecimal deliveryFee,
        Route.RouteSource routeSource,
        WeatherDto weather) {

    public record WeatherDto(WeatherCondition condition, Double temperatureCelsius, BigDecimal surcharge, int extraMinutes) {
    }
}
