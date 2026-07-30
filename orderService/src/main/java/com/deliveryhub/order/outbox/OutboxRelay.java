package com.deliveryhub.order.outbox;

import com.deliveryhub.contracts.EventPayloads;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.errors.RetriableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OutboxRelay {

    public static final String PUBLISH_FAILURES = "outbox.publish.failures";
    public static final String PARKED_TOTAL = "outbox.parked.total";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxProperties properties;
    private final Clock clock;
    private final Counter publishFailures;
    private final Counter parkedEvents;

    public OutboxRelay(OutboxEventRepository outboxEventRepository,
                       KafkaTemplate<String, Object> kafkaTemplate,
                       OutboxProperties properties,
                       Clock clock,
                       MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.clock = clock;
        this.publishFailures = Counter.builder(PUBLISH_FAILURES)
                .description("Failed attempts to publish an outbox event")
                .register(meterRegistry);
        this.parkedEvents = Counter.builder(PARKED_TOTAL)
                .description("Outbox events parked after too many failed attempts")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:1s}")
    @Transactional
    public void publishPending() {
        if (!outboxEventRepository.tryLockRelay(properties.relayLockKey())) {
            log.debug("Another instance is publishing the outbox, skipping this run");
            return;
        }
        Instant now = clock.instant();
        Set<String> heldAggregates = new HashSet<>();
        int published = 0;
        for (OutboxEvent event : outboxEventRepository.lockNextBatch(properties.batchSize())) {
            if (heldAggregates.contains(event.aggregateKey())) {
                continue;
            }
            if (event.isWaiting(now)) {
                heldAggregates.add(event.aggregateKey());
                continue;
            }
            try {
                send(event);
                event.markPublished(now);
                published++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.postpone("interrupted", now, properties.retryBackoff());
                break;
            } catch (Exception e) {
                publishFailures.increment();
                if (isTransient(e)) {
                    log.warn("Broker did not accept outbox event {}, the run is stopped: {}", event.getId(), e.toString());
                    event.postpone(e.toString(), now, properties.retryBackoff());
                    break;
                }
                recordPoison(event, e, now);
                if (!event.isParked()) {
                    heldAggregates.add(event.aggregateKey());
                }
            }
        }
        if (published > 0) {
            log.debug("Published {} outbox events", published);
        }
    }

    private void send(OutboxEvent event) throws Exception {
        kafkaTemplate.send(event.getEventType(), event.getAggregateId(),
                        EventPayloads.decode(event.getEventType(), event.getPayload()))
                .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    private void recordPoison(OutboxEvent event, Exception failure, Instant now) {
        event.recordFailedAttempt(failure.toString(), now,
                properties.backoffAfter(event.getAttempts() + 1), properties.maxAttempts());
        if (event.isParked()) {
            parkedEvents.increment();
            log.error("Outbox event {} of {} is parked after {} attempts",
                    event.getId(), event.aggregateKey(), event.getAttempts(), failure);
        } else {
            log.warn("Outbox event {} failed, attempt {} of {}, next try at {}: {}",
                    event.getId(), event.getAttempts(), properties.maxAttempts(), event.getNextAttemptAt(),
                    failure.toString());
        }
    }

    private static boolean isTransient(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof RetriableException) {
                return true;
            }
            if (cause.getCause() == cause) {
                return false;
            }
        }
        return false;
    }
}
