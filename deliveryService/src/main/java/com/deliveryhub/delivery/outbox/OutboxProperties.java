package com.deliveryhub.delivery.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.outbox")
public record OutboxProperties(
        @DefaultValue("50") int batchSize,
        @DefaultValue("5s") Duration sendTimeout,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("2s") Duration retryBackoff,
        @DefaultValue("1m") Duration maxBackoff,
        @DefaultValue("7302") long relayLockKey) {

    public Duration backoffAfter(int failedAttempts) {
        Duration delay = retryBackoff;
        for (int i = 1; i < failedAttempts && delay.compareTo(maxBackoff) < 0; i++) {
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }
}
