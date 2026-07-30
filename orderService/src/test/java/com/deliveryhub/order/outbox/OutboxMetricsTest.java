package com.deliveryhub.order.outbox;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxMetricsTest {

    private static final Instant NOW = Instant.parse("2026-07-07T10:15:30Z");

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void exposesPendingParkedAndAgeOfOldestEvent() {
        when(outboxEventRepository.countByPublishedAtIsNullAndParkedAtIsNull()).thenReturn(4L);
        when(outboxEventRepository.countByParkedAtIsNotNull()).thenReturn(1L);
        when(outboxEventRepository.findOldestPendingCreatedAt()).thenReturn(Optional.of(NOW.minusSeconds(90)));

        new OutboxMetrics(outboxEventRepository, Clock.fixed(NOW, ZoneOffset.UTC)).bindTo(registry);

        assertThat(registry.get(OutboxMetrics.PENDING).gauge().value()).isEqualTo(4.0);
        assertThat(registry.get(OutboxMetrics.PARKED).gauge().value()).isEqualTo(1.0);
        assertThat(registry.get(OutboxMetrics.OLDEST_PENDING_AGE).gauge().value()).isEqualTo(90.0);
    }

    @Test
    void ageIsZeroWhenNothingIsPending() {
        when(outboxEventRepository.findOldestPendingCreatedAt()).thenReturn(Optional.empty());

        new OutboxMetrics(outboxEventRepository, Clock.fixed(NOW, ZoneOffset.UTC)).bindTo(registry);

        assertThat(registry.get(OutboxMetrics.OLDEST_PENDING_AGE).gauge().value()).isZero();
    }
}
