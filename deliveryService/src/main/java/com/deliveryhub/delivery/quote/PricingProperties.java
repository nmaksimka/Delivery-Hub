package com.deliveryhub.delivery.quote;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

@ConfigurationProperties(prefix = "app.pricing")
public record PricingProperties(BigDecimal baseFee, BigDecimal perKm, Map<WeatherCondition, WeatherRule> weather) {

    public PricingProperties {
        weather = weather == null ? Map.of() : Map.copyOf(weather);
    }

    public WeatherRule ruleFor(WeatherCondition condition) {
        return weather.getOrDefault(condition, WeatherRule.NONE);
    }

    public record WeatherRule(BigDecimal surcharge, int etaPercent) {

        public static final WeatherRule NONE = new WeatherRule(BigDecimal.ZERO, 0);
    }
}
