package com.deliveryhub.payment.infrastructure.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxMetricsTest {

    private static final Instant NOW = Instant.parse("2026-07-20T10:15:30Z");

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);

    @Test
    void parkedGaugeAndParkedCounterAreSeparatePrometheusFamilies() {
        PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        when(outboxEventRepository.countByParkedAtIsNotNull()).thenReturn(2L);
        when(outboxEventRepository.findOldestPendingCreatedAt()).thenReturn(Optional.empty());

        new OutboxMetrics(outboxEventRepository, Clock.fixed(NOW, ZoneOffset.UTC)).bindTo(prometheus);
        Counter.builder(OutboxRelay.PARKED_TOTAL).register(prometheus).increment();

        assertThat(prometheus.scrape()).contains("outbox_parked_current 2.0", "outbox_parked_total 1.0");
    }
}
