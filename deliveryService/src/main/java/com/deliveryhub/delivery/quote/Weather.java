package com.deliveryhub.delivery.quote;

public record Weather(WeatherCondition condition, Double temperatureCelsius) {

    public static Weather unknown() {
        return new Weather(WeatherCondition.UNKNOWN, null);
    }
}
