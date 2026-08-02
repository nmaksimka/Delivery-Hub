package com.deliveryhub.order.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class ExpiredIdempotencyKeysCleanup {

    private static final Logger log = LoggerFactory.getLogger(ExpiredIdempotencyKeysCleanup.class);

    private final IdempotencyKeys keys;
    private final Clock clock;

    public ExpiredIdempotencyKeysCleanup(IdempotencyKeys keys, Clock clock) {
        this.keys = keys;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.idempotency.cleanup-interval:1h}",
            initialDelayString = "${app.cleanup.initial-delay:1m}")
    public void removeExpired() {
        int removed = keys.deleteExpired(clock.instant());
        if (removed > 0) {
            log.info("Removed {} expired idempotency keys", removed);
        }
    }
}
