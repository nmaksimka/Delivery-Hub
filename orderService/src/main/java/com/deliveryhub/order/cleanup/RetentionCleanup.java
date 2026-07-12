package com.deliveryhub.order.cleanup;

import com.deliveryhub.order.inbox.ProcessedEvents;
import com.deliveryhub.order.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
@EnableConfigurationProperties(CleanupProperties.class)
public class RetentionCleanup {

    private static final Logger log = LoggerFactory.getLogger(RetentionCleanup.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEvents processedEvents;
    private final CleanupProperties properties;
    private final Clock clock;

    public RetentionCleanup(OutboxEventRepository outboxEventRepository,
                            ProcessedEvents processedEvents,
                            CleanupProperties properties,
                            Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.processedEvents = processedEvents;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.cleanup.interval:1h}", initialDelayString = "${app.cleanup.initial-delay:1m}")
    @Transactional
    public void removeExpired() {
        Instant now = clock.instant();
        int outboxEvents = outboxEventRepository.deletePublishedBefore(now.minus(properties.outboxRetention()));
        int inboxRecords = processedEvents.deleteProcessedBefore(now.minus(properties.processedEventsRetention()));
        if (outboxEvents > 0 || inboxRecords > 0) {
            log.info("Removed {} published outbox events and {} processed event records", outboxEvents, inboxRecords);
        }
    }
}
