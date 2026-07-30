package com.deliveryhub.restaurant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.catalog-cache")
public record CatalogCacheProperties(@DefaultValue("5m") Duration ttl) {
}
