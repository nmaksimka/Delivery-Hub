package com.deliveryhub.delivery.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.simulator")
public record SimulatorProperties(boolean enabled, Duration tick, double speedKmh, Duration pickupDelay) {

    public SimulatorProperties {
        if (enabled && (tick == null || tick.isNegative() || tick.isZero() || speedKmh <= 0 || pickupDelay == null)) {
            throw new IllegalArgumentException("app.simulator needs positive tick, speed-kmh and pickup-delay");
        }
    }

    public double metersPerSecond() {
        return speedKmh / 3.6;
    }
}
