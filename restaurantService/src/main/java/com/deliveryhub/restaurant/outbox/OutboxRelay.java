package com.deliveryhub.restaurant.outbox;

import com.deliveryhub.contracts.EventPayloads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "app.outbox.relay-enabled", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxRelay(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, Object> kafkaTemplate,
                       OutboxProperties properties, Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:1s}")
    @Transactional
    public void publishPending() {
        if (!outboxEventRepository.tryLockRelay(properties.relayLockKey())) {
            return;
        }
        for (OutboxEvent event : outboxEventRepository.lockNextBatch(properties.batchSize())) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(),
                                EventPayloads.decode(event.getTopic(), event.getPayload()))
                        .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                event.markPublished(clock.instant());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException e) {
                log.warn("Outbox event {} was not published, the run is stopped: {}", event.getId(), e.toString());
                return;
            }
        }
    }
}
