package com.deliveryhub.order.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("app.idempotency")
public record IdempotencyProperties(
        @DefaultValue("24h") Duration ttl,
        @DefaultValue("60s") Duration lease,
        @DefaultValue("64KB") DataSize maxBodySize,
        @DefaultValue List<String> endpoints) {
}
