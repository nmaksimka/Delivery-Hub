package com.deliveryhub.contracts;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.CourierAssignedEvent;
import com.deliveryhub.contracts.events.DeliveryRejectedEvent;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.GeoPoint;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.contracts.events.OrderItemPayload;
import com.deliveryhub.contracts.events.PaymentHeldEvent;
import com.deliveryhub.contracts.events.PaymentReleasedEvent;
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
                new DeliveryStatusUpdatedEvent(UUID.randomUUID(), 7L, 15L, DeliveryStatus.PICKED_UP, NOW, 3L);

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

    @Test
    void orderCancelledKeepsSequence() {
        OrderCancelledEvent event = new OrderCancelledEvent(UUID.randomUUID(), 15L, 42L, NOW, 2L);

        Object decoded = EventPayloads.decode(Topics.ORDER_CANCELLED, EventPayloads.encode(event));

        assertThat(decoded).isEqualTo(event);
        assertThat(((OrderCancelledEvent) decoded).getSequence()).isEqualTo(2L);
    }

    @Test
    void sequenceIsOptionalForOldProducers() {
        assertThat(DeliveryStatusUpdatedEvent.getClassSchema().getField("sequence").defaultVal()).isEqualTo(0L);
        assertThat(OrderCreatedEvent.getClassSchema().getField("sequence").defaultVal()).isEqualTo(0L);
    }

    @Test
    void restaurantLocationIsOptionalForOldProducers() {
        assertThat(OrderCreatedEvent.getClassSchema().getField("restaurantLocation").hasDefaultValue()).isTrue();
        assertThat(PaymentHeldEvent.getClassSchema().getField("restaurantLocation").hasDefaultValue()).isTrue();
        assertThat(PaymentHeldEvent.getClassSchema().getField("restaurantId").hasDefaultValue()).isTrue();
        assertThat(PaymentHeldEvent.getClassSchema().getField("deliveryLocation").hasDefaultValue()).isTrue();
    }

    @Test
    void sagaEventsSurviveRoundTrip() {
        PaymentHeldEvent held = new PaymentHeldEvent(UUID.randomUUID(), 15L, 42L, new BigDecimal("256.00"), "demo", NOW,
                3L, new GeoPoint(56.4884, 84.948), new GeoPoint(56.4697, 84.9483));
        CourierAssignedEvent assigned = new CourierAssignedEvent(UUID.randomUUID(), 7L, 15L, 9001L, NOW, 1L);
        DeliveryRejectedEvent rejected = new DeliveryRejectedEvent(UUID.randomUUID(), 15L, 42L, "NO_FREE_COURIER", NOW);

        assertThat(EventPayloads.decode(Topics.PAYMENT_HELD, EventPayloads.encode(held))).isEqualTo(held);
        assertThat(EventPayloads.decode(Topics.COURIER_ASSIGNED, EventPayloads.encode(assigned))).isEqualTo(assigned);
        assertThat(EventPayloads.decode(Topics.DELIVERY_REJECTED, EventPayloads.encode(rejected))).isEqualTo(rejected);
    }

    @Test
    void releaseReasonDefaultsToOrderCancellation() {
        assertThat(PaymentReleasedEvent.getClassSchema().getField("reason").defaultVal()).isEqualTo("ORDER_CANCELLED");
    }

    @Test
    void deadLetterTopicHasSuffix() {
        assertThat(Topics.deadLetterOf(Topics.ORDER_CREATED)).isEqualTo("order-created.DLT");
    }
}
