package com.deliveryhub.order.outbox;

import com.deliveryhub.contracts.EventPayloads;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-07-07T10:15:30Z");
    private static final int BATCH_SIZE = 10;

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outboxEventRepository, kafkaTemplate,
                new OutboxProperties(BATCH_SIZE, Duration.ofSeconds(1)), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void publishesPendingEventToTopicNamedByEventTypeAndMarksItPublished() {
        OutboxEvent event = pendingEvent("15");
        when(outboxEventRepository.lockNextBatch(BATCH_SIZE)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.publishPending();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("order-created"), eq("15"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(OrderCreatedEvent.class);
        assertThat(((OrderCreatedEvent) payload.getValue()).getOrderId()).isEqualTo(15L);
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void leavesEventPendingWhenBrokerRejectsIt() {
        OutboxEvent event = pendingEvent("15");
        when(outboxEventRepository.lockNextBatch(BATCH_SIZE)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker is down")));

        assertThatThrownBy(() -> relay.publishPending())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(event.getId().toString());
        assertThat(event.isPublished()).isFalse();
    }

    @Test
    void stopsAtFirstFailureSoLaterEventsKeepTheirOrder() {
        OutboxEvent first = pendingEvent("15");
        OutboxEvent second = pendingEvent("16");
        when(outboxEventRepository.lockNextBatch(BATCH_SIZE)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(any(String.class), eq("15"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker is down")));

        assertThatThrownBy(() -> relay.publishPending()).isInstanceOf(IllegalStateException.class);

        assertThat(second.isPublished()).isFalse();
    }

    @Test
    void doesNothingWhenOutboxIsEmpty() {
        when(outboxEventRepository.lockNextBatch(BATCH_SIZE)).thenReturn(List.of());

        relay.publishPending();

        verifyNoInteractions(kafkaTemplate);
    }

    private static OutboxEvent pendingEvent(String orderId) {
        return new OutboxEvent(UUID.randomUUID(), "Order", orderId, "order-created",
                orderCreatedPayload(orderId), NOW.minusSeconds(5));
    }

    private static byte[] orderCreatedPayload(String orderId) {
        return EventPayloads.encode(OrderCreatedEvent.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOrderId(Long.parseLong(orderId))
                .setUserId(42L)
                .setRestaurantId(1L)
                .setTotalAmount(new BigDecimal("34.00"))
                .setCreatedAt(NOW)
                .setItems(List.of())
                .build());
    }
}
