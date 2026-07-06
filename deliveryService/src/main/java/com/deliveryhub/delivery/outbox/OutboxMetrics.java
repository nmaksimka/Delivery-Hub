package com.deliveryhub.delivery.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics implements MeterBinder {

    public static final String PENDING = "outbox.pending";

    private final OutboxEventRepository outboxEventRepository;

    public OutboxMetrics(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(PENDING, outboxEventRepository, OutboxEventRepository::countByPublishedAtIsNull)
                .description("Outbox events that are not published to Kafka yet")
                .strongReference(true)
                .register(registry);
    }
}
