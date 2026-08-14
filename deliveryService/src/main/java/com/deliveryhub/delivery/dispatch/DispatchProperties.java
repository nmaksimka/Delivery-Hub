package com.deliveryhub.delivery.dispatch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.dispatch")
public record DispatchProperties(@DefaultValue("5000") int zoneRadiusMeters) {

    public DispatchProperties {
        if (zoneRadiusMeters <= 0) {
            throw new IllegalArgumentException("app.dispatch.zone-radius-meters must be positive");
        }
    }
}
