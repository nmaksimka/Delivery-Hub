package com.deliveryhub.order.cleanup;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.cleanup")
public record CleanupProperties(
        @DefaultValue("7d") Duration outboxRetention,
        @DefaultValue("14d") Duration processedEventsRetention) {
}
