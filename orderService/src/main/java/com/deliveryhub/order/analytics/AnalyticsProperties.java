package com.deliveryhub.order.analytics;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

@ConfigurationProperties("app.analytics")
public record AnalyticsProperties(
        @DefaultValue("Asia/Tomsk") ZoneId zone,
        @DefaultValue("366") int maxPeriodDays,
        @DefaultValue("30") int defaultPeriodDays,
        @DefaultValue("10") int topDishesLimit) {
}
