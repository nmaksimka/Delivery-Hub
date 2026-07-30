package com.deliveryhub.order.cleanup;

import com.deliveryhub.order.inbox.ProcessedEvents;
import com.deliveryhub.order.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RetentionCleanupTest {

    private static final Instant NOW = Instant.parse("2026-07-20T03:00:00Z");

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final ProcessedEvents processedEvents = mock(ProcessedEvents.class);

    @Test
    void removesRecordsOlderThanTheirRetention() {
        RetentionCleanup cleanup = new RetentionCleanup(outboxEventRepository, processedEvents,
                new CleanupProperties(Duration.ofDays(7), Duration.ofDays(14)), Clock.fixed(NOW, ZoneOffset.UTC));

        cleanup.removeExpired();

        verify(outboxEventRepository).deletePublishedBefore(Instant.parse("2026-07-13T03:00:00Z"));
        verify(processedEvents).deleteProcessedBefore(Instant.parse("2026-07-06T03:00:00Z"));
    }
}
