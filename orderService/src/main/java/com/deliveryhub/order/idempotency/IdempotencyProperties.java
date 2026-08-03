package com.deliveryhub.order.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("app.idempotency")
public record IdempotencyProperties(
        @DefaultValue("24h") Duration ttl,
        @DefaultValue List<String> endpoints) {
}
