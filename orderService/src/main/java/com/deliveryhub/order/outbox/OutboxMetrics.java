package com.deliveryhub.order.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
public class OutboxMetrics implements MeterBinder {

    public static final String PENDING = "outbox.pending";
    public static final String PARKED = "outbox.parked";
    public static final String OLDEST_PENDING_AGE = "outbox.oldest.pending.age";

    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock;

    public OutboxMetrics(OutboxEventRepository outboxEventRepository, Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.clock = clock;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(PENDING, outboxEventRepository,
                        OutboxEventRepository::countByPublishedAtIsNullAndParkedAtIsNull)
                .description("Outbox events that are not published to Kafka yet")
                .strongReference(true)
                .register(registry);
        Gauge.builder(PARKED, outboxEventRepository, OutboxEventRepository::countByParkedAtIsNotNull)
                .description("Outbox events that need manual attention")
                .strongReference(true)
                .register(registry);
        Gauge.builder(OLDEST_PENDING_AGE, this, OutboxMetrics::oldestPendingAgeSeconds)
                .description("Age of the oldest unpublished outbox event")
                .baseUnit("seconds")
                .strongReference(true)
                .register(registry);
    }

    private double oldestPendingAgeSeconds() {
        return outboxEventRepository.findOldestPendingCreatedAt()
                .map(createdAt -> Duration.between(createdAt, clock.instant()).toMillis() / 1000.0)
                .map(age -> Math.max(age, 0.0))
                .orElse(0.0);
    }
}
