package com.deliveryhub.order.cart;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.cart")
public record CartProperties(@DefaultValue("7d") Duration ttl) {
}
