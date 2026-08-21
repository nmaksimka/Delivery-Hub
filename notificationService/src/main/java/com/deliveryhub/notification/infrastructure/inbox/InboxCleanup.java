package com.deliveryhub.notification.infrastructure.inbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

@Component
public class InboxCleanup {

    private static final Logger log = LoggerFactory.getLogger(InboxCleanup.class);

    private final ProcessedEvents processedEvents;
    private final Duration retention;
    private final Clock clock;

    public InboxCleanup(ProcessedEvents processedEvents,
                        @Value("${app.cleanup.processed-events-retention:14d}") Duration retention,
                        Clock clock) {
        this.processedEvents = processedEvents;
        this.retention = retention;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.cleanup.interval:1h}", initialDelayString = "${app.cleanup.initial-delay:1m}")
    @Transactional
    public void removeExpired() {
        int removed = processedEvents.deleteProcessedBefore(clock.instant().minus(retention));
        if (removed > 0) {
            log.info("Removed {} processed event records", removed);
        }
    }
}
