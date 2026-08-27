package com.deliveryhub.notification.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private static final Duration BACKOFF = Duration.ofSeconds(10);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

    @Test
    void newNotificationWaitsForMailRightAway() {
        Notification notification = Notification.orderCreated(UUID.randomUUID(), 7L, 42L, new BigDecimal("990.50"), NOW);

        assertThat(notification.getMailStatus()).isEqualTo(MailStatus.PENDING);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(notification.getTitle()).contains("42");
        assertThat(notification.getMessage()).contains("990.50");
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    void failedMailIsRetriedWithGrowingBackoffCappedByMax() {
        Notification notification = Notification.courierAssigned(UUID.randomUUID(), 7L, 42L, NOW);

        notification.mailFailed("smtp down", NOW, BACKOFF, MAX_BACKOFF, 5);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));

        notification.mailFailed("smtp down", NOW, BACKOFF, MAX_BACKOFF, 5);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(20));

        notification.mailFailed("smtp down", NOW, BACKOFF, MAX_BACKOFF, 5);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(notification.getMailStatus()).isEqualTo(MailStatus.PENDING);
        assertThat(notification.getLastError()).isEqualTo("smtp down");
    }

    @Test
    void mailGivesUpAfterMaxAttempts() {
        Notification notification = Notification.orderDelivered(UUID.randomUUID(), 7L, 42L, NOW);

        notification.mailFailed("first", NOW, BACKOFF, MAX_BACKOFF, 2);
        notification.mailFailed("x".repeat(900), NOW, BACKOFF, MAX_BACKOFF, 2);

        assertThat(notification.getMailStatus()).isEqualTo(MailStatus.FAILED);
        assertThat(notification.getMailAttempts()).isEqualTo(2);
        assertThat(notification.getLastError()).hasSize(500);
    }

    @Test
    void sentMailClearsLastError() {
        Notification notification = Notification.orderCancelled(UUID.randomUUID(), 7L, 42L, NOW);
        notification.mailFailed("timeout", NOW, BACKOFF, MAX_BACKOFF, 5);

        notification.mailSent(NOW.plusSeconds(15));

        assertThat(notification.getMailStatus()).isEqualTo(MailStatus.SENT);
        assertThat(notification.getSentAt()).isEqualTo(NOW.plusSeconds(15));
        assertThat(notification.getLastError()).isNull();
    }
}
