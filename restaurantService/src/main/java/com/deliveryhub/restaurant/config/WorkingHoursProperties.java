package com.deliveryhub.restaurant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@ConfigurationProperties("app.working-hours")
public record WorkingHoursProperties(ZoneId zone) {

    public WorkingHoursProperties {
        if (zone == null) {
            zone = ZoneId.of("Asia/Tomsk");
        }
    }
}
