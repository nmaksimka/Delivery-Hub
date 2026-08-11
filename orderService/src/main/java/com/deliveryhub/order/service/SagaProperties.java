package com.deliveryhub.order.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.saga")
public record SagaProperties(
        @DefaultValue("2m") Duration paymentTimeout,
        @DefaultValue("5m") Duration courierTimeout,
        @DefaultValue("2m") Duration confirmationTimeout,
        @DefaultValue("100") int batchSize) {
}
