package com.deliveryhub.order.outbox;

import com.deliveryhub.contracts.EventPayloads;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.errors.NetworkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-07-07T10:15:30Z");
    private static final int BATCH_SIZE = 10;
    private static final int MAX_ATTEMPTS = 3;
    private static final long LOCK_KEY = 77L;
    private static final String TOPIC = "order-created";

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(BATCH_SIZE, Duration.ofSeconds(1), MAX_ATTEMPTS,
                Duration.ofSeconds(2), Duration.ofSeconds(5), LOCK_KEY);
        relay = new OutboxRelay(outboxEventRepository, kafkaTemplate, properties,
                Clock.fixed(NOW, ZoneOffset.UTC), meterRegistry);
        lenient().when(outboxEventRepository.tryLockRelay(LOCK_KEY)).thenReturn(true);
        lenient().when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void publishesPendingEventToTopicNamedByEventTypeAndMarksItPublished() {
        OutboxEvent event = pendingEvent("15");
        batchIs(event);

        relay.publishPending();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq("15"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(OrderCreatedEvent.class);
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void doesNothingWhenAnotherInstanceHoldsRelayLock() {
        when(outboxEventRepository.tryLockRelay(LOCK_KEY)).thenReturn(false);

        relay.publishPending();

        verify(outboxEventRepository, never()).lockNextBatch(BATCH_SIZE);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void poisonEventGetsBackoffAndHoldsLaterEventsOfItsAggregateOnly() {
        OutboxEvent poison = poisonEvent("15");
        OutboxEvent sameAggregate = pendingEvent("15");
        OutboxEvent otherAggregate = pendingEvent("16");
        batchIs(poison, sameAggregate, otherAggregate);

        relay.publishPending();

        assertThat(poison.getAttempts()).isEqualTo(1);
        assertThat(poison.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(poison.getLastError()).isNotBlank();
        assertThat(poison.isParked()).isFalse();
        assertThat(sameAggregate.isPublished()).isFalse();
        assertThat(otherAggregate.isPublished()).isTrue();
        assertThat(meterRegistry.get(OutboxRelay.PUBLISH_FAILURES).counter().count()).isEqualTo(1.0);
    }

    @Test
    void backoffGrowsWithAttemptsUpToLimit() {
        OutboxEvent poison = poisonEvent("15");
        poison.recordFailedAttempt("first", NOW.minusSeconds(60), Duration.ofSeconds(2), MAX_ATTEMPTS);
        batchIs(poison);

        relay.publishPending();

        assertThat(poison.getAttempts()).isEqualTo(2);
        assertThat(poison.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(4));
    }

    @Test
    void parksPoisonEventAfterLastAttemptAndLetsAggregateContinue() {
        OutboxEvent poison = poisonEvent("15");
        poison.recordFailedAttempt("first", NOW.minusSeconds(60), Duration.ofSeconds(2), MAX_ATTEMPTS);
        poison.recordFailedAttempt("second", NOW.minusSeconds(30), Duration.ofSeconds(4), MAX_ATTEMPTS);
        OutboxEvent next = pendingEvent("15");
        batchIs(poison, next);

        relay.publishPending();

        assertThat(poison.isParked()).isTrue();
        assertThat(poison.getParkedAt()).isEqualTo(NOW);
        assertThat(poison.getNextAttemptAt()).isNull();
        assertThat(next.isPublished()).isTrue();
        assertThat(meterRegistry.get(OutboxRelay.PARKED_TOTAL).counter().count()).isEqualTo(1.0);
    }

    @Test
    void eventWaitingForRetryIsSkippedTogetherWithItsAggregate() {
        OutboxEvent waiting = pendingEvent("15");
        waiting.recordFailedAttempt("first", NOW.minusSeconds(1), Duration.ofSeconds(30), MAX_ATTEMPTS);
        OutboxEvent sameAggregate = pendingEvent("15");
        batchIs(waiting, sameAggregate);

        relay.publishPending();

        verifyNoInteractions(kafkaTemplate);
        assertThat(waiting.getAttempts()).isEqualTo(1);
        assertThat(sameAggregate.isPublished()).isFalse();
    }

    @Test
    void brokerOutageStopsRunWithoutSpendingAttempts() {
        OutboxEvent first = pendingEvent("15");
        OutboxEvent second = pendingEvent("16");
        batchIs(first, second);
        when(kafkaTemplate.send(any(String.class), eq("15"), any()))
                .thenReturn(CompletableFuture.failedFuture(new NetworkException("broker is down")));

        relay.publishPending();

        assertThat(first.getAttempts()).isZero();
        assertThat(first.isParked()).isFalse();
        assertThat(first.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(second.isPublished()).isFalse();
        verify(kafkaTemplate, never()).send(any(String.class), eq("16"), any());
    }

    @Test
    void sendTimeoutIsTreatedAsOutage() {
        OutboxEvent event = pendingEvent("15");
        batchIs(event);
        when(kafkaTemplate.send(any(String.class), eq("15"), any()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("no answer")));

        relay.publishPending();

        assertThat(event.getAttempts()).isZero();
        assertThat(event.isPublished()).isFalse();
    }

    @Test
    void doesNothingWhenOutboxIsEmpty() {
        batchIs();

        relay.publishPending();

        verifyNoInteractions(kafkaTemplate);
    }

    private void batchIs(OutboxEvent... events) {
        when(outboxEventRepository.lockNextBatch(BATCH_SIZE)).thenReturn(List.of(events));
    }

    private static OutboxEvent pendingEvent(String aggregateId) {
        return new OutboxEvent(UUID.randomUUID(), "Order", aggregateId, TOPIC,
                payload(aggregateId), NOW.minusSeconds(5));
    }

    private static OutboxEvent poisonEvent(String aggregateId) {
        return new OutboxEvent(UUID.randomUUID(), "Order", aggregateId, TOPIC,
                new byte[]{1, 2, 3}, NOW.minusSeconds(5));
    }

    private static byte[] payload(String aggregateId) {
        return EventPayloads.encode(OrderCreatedEvent.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOrderId(Long.parseLong(aggregateId))
                .setUserId(42L)
                .setRestaurantId(1L)
                .setTotalAmount(new java.math.BigDecimal("34.00"))
                .setCreatedAt(NOW)
                .setItems(List.of())
                .build());
    }
}
