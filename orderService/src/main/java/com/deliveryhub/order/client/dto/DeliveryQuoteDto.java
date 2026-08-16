package com.deliveryhub.order.client.dto;

import java.math.BigDecimal;

public record DeliveryQuoteDto(
        int distanceMeters,
        int etaMinutes,
        BigDecimal deliveryFee,
        String routeSource,
        Weather weather) {

    public record Weather(String condition, Double temperatureCelsius, BigDecimal surcharge, int extraMinutes) {
    }
}
