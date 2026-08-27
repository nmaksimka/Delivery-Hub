package com.deliveryhub.notification.infrastructure.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.mail")
public record MailProperties(String from, Duration pollInterval, int batchSize, int maxAttempts,
                             Duration retryBackoff, Duration maxBackoff) {
}
