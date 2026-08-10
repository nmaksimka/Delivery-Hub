package com.deliveryhub.delivery.cleanup;

import com.deliveryhub.delivery.inbox.ProcessedEvents;
import com.deliveryhub.delivery.outbox.OutboxEventRepository;
import com.deliveryhub.delivery.repository.CancelledOrders;
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
    private final CancelledOrders cancelledOrders;
    private final CleanupProperties properties;
    private final Clock clock;

    public RetentionCleanup(OutboxEventRepository outboxEventRepository,
                            ProcessedEvents processedEvents,
                            CancelledOrders cancelledOrders,
                            CleanupProperties properties,
                            Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.processedEvents = processedEvents;
        this.cancelledOrders = cancelledOrders;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.cleanup.interval:1h}", initialDelayString = "${app.cleanup.initial-delay:1m}")
    @Transactional
    public void removeExpired() {
        Instant now = clock.instant();
        int outboxEvents = outboxEventRepository.deletePublishedBefore(now.minus(properties.outboxRetention()));
        Instant inboxCutoff = now.minus(properties.processedEventsRetention());
        int inboxRecords = processedEvents.deleteProcessedBefore(inboxCutoff);
        int cancellations = cancelledOrders.forgetCancelledBefore(inboxCutoff);
        if (outboxEvents > 0 || inboxRecords > 0 || cancellations > 0) {
            log.info("Removed {} published outbox events, {} processed event records and {} early cancellations",
                    outboxEvents, inboxRecords, cancellations);
        }
    }
}
