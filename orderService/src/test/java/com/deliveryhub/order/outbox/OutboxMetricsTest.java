package com.deliveryhub.order.outbox;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxMetricsTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Test
    void gaugeFollowsNumberOfUnpublishedEvents() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new OutboxMetrics(outboxEventRepository).bindTo(registry);

        when(outboxEventRepository.countByPublishedAtIsNull()).thenReturn(3L);
        assertThat(registry.get(OutboxMetrics.PENDING).gauge().value()).isEqualTo(3.0);

        when(outboxEventRepository.countByPublishedAtIsNull()).thenReturn(0L);
        assertThat(registry.get(OutboxMetrics.PENDING).gauge().value()).isZero();
    }
}
