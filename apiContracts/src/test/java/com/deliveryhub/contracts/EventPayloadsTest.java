package com.deliveryhub.contracts;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.contracts.events.OrderItemPayload;
import org.apache.avro.AvroRuntimeException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventPayloadsTest {

    private static final Instant NOW = Instant.parse("2026-07-09T10:15:30.123Z");

    @Test
    void orderCreatedSurvivesRoundTripWithMoneyAndTimestamps() {
        OrderCreatedEvent event = OrderCreatedEvent.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOrderId(15L)
                .setUserId(42L)
                .setRestaurantId(1L)
                .setTotalAmount(new BigDecimal("256.00"))
                .setCreatedAt(NOW)
                .setItems(List.of(new OrderItemPayload(10L, "Пицца", 2, new BigDecimal("128.00"))))
                .build();

        byte[] payload = EventPayloads.encode(event);

        assertThat(EventPayloads.decode(Topics.ORDER_CREATED, payload)).isEqualTo(event);
    }

    @Test
    void deliveryStatusSurvivesRoundTrip() {
        DeliveryStatusUpdatedEvent event =
                new DeliveryStatusUpdatedEvent(UUID.randomUUID(), 7L, 15L, DeliveryStatus.PICKED_UP, NOW);

        byte[] payload = EventPayloads.encode(event);

        assertThat(EventPayloads.decode(Topics.DELIVERY_STATUS_UPDATED, payload)).isEqualTo(event);
    }

    @Test
    void refusesPayloadWrittenWithAnotherSchema() {
        byte[] orderCreated = EventPayloads.encode(OrderCreatedEvent.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOrderId(15L)
                .setUserId(42L)
                .setRestaurantId(1L)
                .setTotalAmount(BigDecimal.TEN.setScale(2))
                .setCreatedAt(NOW)
                .setItems(List.of())
                .build());

        assertThatThrownBy(() -> EventPayloads.decode(Topics.DELIVERY_STATUS_UPDATED, orderCreated))
                .isInstanceOf(AvroRuntimeException.class);
    }

    @Test
    void refusesUnknownTopic() {
        assertThatThrownBy(() -> EventPayloads.decode("payments", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payments");
    }
}
