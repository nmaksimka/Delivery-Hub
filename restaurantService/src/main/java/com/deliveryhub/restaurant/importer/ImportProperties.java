package com.deliveryhub.restaurant.importer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.import")
public record ImportProperties(@DefaultValue("10m") Duration staleAfter) {
}
