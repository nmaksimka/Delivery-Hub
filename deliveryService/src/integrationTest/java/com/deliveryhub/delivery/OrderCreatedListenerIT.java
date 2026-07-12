package com.deliveryhub.delivery;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.contracts.events.OrderItemPayload;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class OrderCreatedListenerIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private KafkaContainer kafka;

    @Test
    void createsDeliveryFromOrderCreatedEventAndReportsStatusBack() {
        long orderId = 7001L;

        kafkaTemplate.send(Topics.ORDER_CREATED, Long.toString(orderId), orderCreated(orderId));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(deliveryRepository.findByOrderId(orderId)).get()
                        .satisfies(delivery -> assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED)));

        Optional<DeliveryStatusUpdatedEvent> statusUpdate = new KafkaTopicReader(kafka.getBootstrapServers())
                .findFirst(Topics.DELIVERY_STATUS_UPDATED, DeliveryStatusUpdatedEvent.class,
                        event -> event.getOrderId() == orderId);

        assertThat(statusUpdate).isPresent();
        assertThat(statusUpdate.get().getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(statusUpdate.get().getEventId()).isNotNull();
    }

    @Test
    void repeatedEventDoesNotCreateSecondDelivery() {
        long orderId = 7002L;
        long marker = 7003L;

        kafkaTemplate.send(Topics.ORDER_CREATED, Long.toString(orderId), orderCreated(orderId));
        kafkaTemplate.send(Topics.ORDER_CREATED, Long.toString(orderId), orderCreated(orderId));
        kafkaTemplate.send(Topics.ORDER_CREATED, Long.toString(orderId), orderCreated(marker));

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

        kafkaTemplate.send(Topics.ORDER_CREATED, "same-key", orderCreated(eventId, firstOrderId));
        kafkaTemplate.send(Topics.ORDER_CREATED, "same-key", orderCreated(eventId, secondOrderId));
        kafkaTemplate.send(Topics.ORDER_CREATED, "same-key", orderCreated(marker));

        await().atMost(TIMEOUT).until(() -> deliveryRepository.existsByOrderId(marker));

        assertThat(deliveryRepository.existsByOrderId(firstOrderId)).isTrue();
        assertThat(deliveryRepository.existsByOrderId(secondOrderId)).isFalse();
    }

    private static OrderCreatedEvent orderCreated(long orderId) {
        return orderCreated(UUID.randomUUID(), orderId);
    }

    private static OrderCreatedEvent orderCreated(UUID eventId, long orderId) {
        return new OrderCreatedEvent(eventId, orderId, 42L, 1L, new BigDecimal("34.00"), Instant.now(),
                List.of(new OrderItemPayload(10L, "Pizza", 2, new BigDecimal("12.50"))), 1L);
    }
}
