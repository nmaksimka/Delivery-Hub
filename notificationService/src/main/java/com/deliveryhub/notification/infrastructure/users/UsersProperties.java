package com.deliveryhub.notification.infrastructure.users;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.users")
public record UsersProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
