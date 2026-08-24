package com.deliveryhub.restaurant.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.outbox")
public record OutboxProperties(
        @DefaultValue("50") int batchSize,
        @DefaultValue("5s") Duration sendTimeout,
        @DefaultValue("7331") long relayLockKey) {
}
