package com.deliveryhub.delivery;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.CourierAssignedEvent;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
import com.deliveryhub.contracts.events.PaymentHeldEvent;
import com.deliveryhub.delivery.repository.CancelledOrders;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class SagaListenerIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private CancelledOrders cancelledOrders;
    @Autowired
    private KafkaContainer kafka;

    @Test
    void assignsCourierAfterPaymentIsHeldAndAnnouncesIt() {
        long orderId = 7001L;

        kafkaTemplate.send(Topics.PAYMENT_HELD, Long.toString(orderId), paymentHeld(orderId));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(deliveryRepository.findByOrderId(orderId)).get()
                        .satisfies(delivery -> assertThat(delivery.getCourierId()).isNotNull()));

        Optional<CourierAssignedEvent> assigned = new KafkaTopicReader(kafka.getBootstrapServers())
                .findFirst(Topics.COURIER_ASSIGNED, CourierAssignedEvent.class, event -> event.getOrderId() == orderId);

        assertThat(assigned).isPresent();
        assertThat(assigned.get().getCourierId())
                .isEqualTo(deliveryRepository.findByOrderId(orderId).orElseThrow().getCourierId());
        assertThat(assigned.get().getSequence()).isEqualTo(1L);
    }

    @Test
    void repeatedPaymentHeldDoesNotCreateSecondDelivery() {
        long orderId = 7002L;
        long marker = 7003L;

        kafkaTemplate.send(Topics.PAYMENT_HELD, Long.toString(orderId), paymentHeld(orderId));
        kafkaTemplate.send(Topics.PAYMENT_HELD, Long.toString(orderId), paymentHeld(orderId));
        kafkaTemplate.send(Topics.PAYMENT_HELD, Long.toString(orderId), paymentHeld(marker));

        await().atMost(TIMEOUT).until(() -> deliveryRepository.existsByOrderId(marker));

        assertThat(deliveryRepository.findAll())
                .filteredOn(delivery -> delivery.getOrderId().equals(orderId))
                .hasSize(1);
    }

    @Test
    void ignoresEventWhoseIdWasAlreadyProcessed() {
        UUID eventId = UUID.randomUUID();
        long firstOrderId = 7004L;
        long secondOrderId = 7005L;
        long marker = 7006L;

        kafkaTemplate.send(Topics.PAYMENT_HELD, "same-key", paymentHeld(eventId, firstOrderId));
        kafkaTemplate.send(Topics.PAYMENT_HELD, "same-key", paymentHeld(eventId, secondOrderId));
        kafkaTemplate.send(Topics.PAYMENT_HELD, "same-key", paymentHeld(marker));

        await().atMost(TIMEOUT).until(() -> deliveryRepository.existsByOrderId(marker));

        assertThat(deliveryRepository.existsByOrderId(firstOrderId)).isTrue();
        assertThat(deliveryRepository.existsByOrderId(secondOrderId)).isFalse();
    }

    @Test
    void cancelsDeliveryWhenOrderIsCancelledAndReportsItBack() {
        long orderId = 7010L;
        kafkaTemplate.send(Topics.PAYMENT_HELD, Long.toString(orderId), paymentHeld(orderId));
        await().atMost(TIMEOUT).until(() -> deliveryRepository.existsByOrderId(orderId));

        kafkaTemplate.send(Topics.ORDER_CANCELLED, Long.toString(orderId),
                new OrderCancelledEvent(UUID.randomUUID(), orderId, 42L, Instant.now(), 2L));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(deliveryRepository.findByOrderId(orderId)).get()
                        .satisfies(delivery -> assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELLED)));

        Optional<DeliveryStatusUpdatedEvent> statusUpdate = new KafkaTopicReader(kafka.getBootstrapServers())
                .findFirst(Topics.DELIVERY_STATUS_UPDATED, DeliveryStatusUpdatedEvent.class,
                        event -> event.getOrderId() == orderId && event.getStatus() == DeliveryStatus.CANCELLED);

        assertThat(statusUpdate).isPresent();
        assertThat(statusUpdate.get().getSequence()).isEqualTo(2L);
    }

    @Test
    void cancellationBeforePaymentHeldKeepsCourierFreeWithoutStubDelivery() {
        long orderId = 7020L;
        long marker = 7021L;
        kafkaTemplate.send(Topics.ORDER_CANCELLED, Long.toString(orderId),
                new OrderCancelledEvent(UUID.randomUUID(), orderId, 42L, Instant.now(), 2L));
        await().atMost(TIMEOUT).until(() -> cancelledOrders.contains(orderId));

        kafkaTemplate.send(Topics.PAYMENT_HELD, Long.toString(orderId), paymentHeld(orderId));
        kafkaTemplate.send(Topics.PAYMENT_HELD, Long.toString(orderId), paymentHeld(marker));
        await().atMost(TIMEOUT).until(() -> deliveryRepository.existsByOrderId(marker));

        assertThat(deliveryRepository.existsByOrderId(orderId)).isFalse();
    }

    private static PaymentHeldEvent paymentHeld(long orderId) {
        return paymentHeld(UUID.randomUUID(), orderId);
    }

    private static PaymentHeldEvent paymentHeld(UUID eventId, long orderId) {
        return new PaymentHeldEvent(eventId, orderId, 42L, new BigDecimal("34.00"), "demo", Instant.now(), null, null);
    }
}
