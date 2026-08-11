package com.deliveryhub.payment;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.CourierAssignedEvent;
import com.deliveryhub.contracts.events.DeliveryRejectedEvent;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.contracts.events.PaymentCapturedEvent;
import com.deliveryhub.contracts.events.PaymentHeldEvent;
import com.deliveryhub.contracts.events.PaymentRefundedEvent;
import com.deliveryhub.contracts.events.PaymentRejectedEvent;
import com.deliveryhub.contracts.events.PaymentReleasedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class, KafkaContainerConfig.class})
class PaymentSagaIT extends PaymentApiSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private KafkaContainer kafka;

    @Test
    void holdsMoneyForCreatedOrderAndAnnouncesIt() {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "500");

        send(Topics.ORDER_CREATED, orderId, orderCreated(UUID.randomUUID(), orderId, userId, "120.00"));

        Optional<PaymentHeldEvent> held = reader().findFirst(Topics.PAYMENT_HELD, PaymentHeldEvent.class,
                event -> event.getOrderId() == orderId);
        assertThat(held).isPresent();
        assertThat(held.get().getAmount()).isEqualByComparingTo("120.00");
        assertThat(held(userId)).isEqualByComparingTo("120.00");
    }

    @Test
    void rejectsOrderWhenWalletHasNoMoney() {
        long userId = nextId();
        long orderId = nextId();

        send(Topics.ORDER_CREATED, orderId, orderCreated(UUID.randomUUID(), orderId, userId, "50.00"));

        Optional<PaymentRejectedEvent> rejected = reader().findFirst(Topics.PAYMENT_REJECTED,
                PaymentRejectedEvent.class, event -> event.getOrderId() == orderId);
        assertThat(rejected).isPresent();
        assertThat(rejected.get().getReason()).isEqualTo("PAYMENT_INSUFFICIENT_FUNDS");
        assertThat(statusOf(orderId)).isEqualTo("REJECTED");
    }

    @Test
    void duplicateOrderCreatedHoldsMoneyOnce() {
        long userId = nextId();
        long orderId = nextId();
        long markerOrderId = nextId();
        fund(userId, "500");
        OrderCreatedEvent event = orderCreated(UUID.randomUUID(), orderId, userId, "100.00");

        send(Topics.ORDER_CREATED, orderId, event);
        send(Topics.ORDER_CREATED, orderId, event);
        send(Topics.ORDER_CREATED, orderId, orderCreated(UUID.randomUUID(), markerOrderId, userId, "10.00"));

        await().atMost(TIMEOUT).until(() -> paymentExists(markerOrderId));
        assertThat(operations(userId, "HOLD")).isEqualTo(2);
        assertThat(held(userId)).isEqualByComparingTo("110.00");
    }

    @Test
    void capturesHeldPaymentWhenCourierIsAssigned() {
        long userId = nextId();
        long orderId = heldOrder(userId, "80.00");

        send(Topics.COURIER_ASSIGNED, orderId,
                new CourierAssignedEvent(UUID.randomUUID(), 1L, orderId, 9001L, Instant.now(), 1L));

        Optional<PaymentCapturedEvent> captured = reader().findFirst(Topics.PAYMENT_CAPTURED,
                PaymentCapturedEvent.class, event -> event.getOrderId() == orderId);
        assertThat(captured).isPresent();
        assertThat(statusOf(orderId)).isEqualTo("CAPTURED");
        assertThat(held(userId)).isEqualByComparingTo("0");
    }

    @Test
    void releasesHoldWhenDeliveryIsRejected() {
        long userId = nextId();
        long orderId = heldOrder(userId, "70.00");

        send(Topics.DELIVERY_REJECTED, orderId,
                new DeliveryRejectedEvent(UUID.randomUUID(), orderId, userId, "NO_FREE_COURIER", Instant.now()));

        Optional<PaymentReleasedEvent> released = reader().findFirst(Topics.PAYMENT_RELEASED,
                PaymentReleasedEvent.class, event -> event.getOrderId() == orderId);
        assertThat(released).isPresent();
        assertThat(released.get().getReason()).isEqualTo("DELIVERY_REJECTED");
        assertThat(available(userId)).isEqualByComparingTo("500");
    }

    @Test
    void refundsCapturedPaymentWhenOrderIsCancelled() {
        long userId = nextId();
        long orderId = heldOrder(userId, "60.00");
        send(Topics.COURIER_ASSIGNED, orderId,
                new CourierAssignedEvent(UUID.randomUUID(), 1L, orderId, 9001L, Instant.now(), 1L));
        await().atMost(TIMEOUT).until(() -> "CAPTURED".equals(statusOf(orderId)));

        send(Topics.ORDER_CANCELLED, orderId, new OrderCancelledEvent(UUID.randomUUID(), orderId, userId,
                Instant.now(), 2L));

        Optional<PaymentRefundedEvent> refunded = reader().findFirst(Topics.PAYMENT_REFUNDED,
                PaymentRefundedEvent.class, event -> event.getOrderId() == orderId);
        assertThat(refunded).isPresent();
        assertThat(available(userId)).isEqualByComparingTo("500");
    }

    @Test
    void lateCourierAssignmentDoesNotCaptureReleasedPayment() {
        long userId = nextId();
        long orderId = heldOrder(userId, "40.00");
        send(Topics.ORDER_CANCELLED, orderId, new OrderCancelledEvent(UUID.randomUUID(), orderId, userId,
                Instant.now(), 2L));
        await().atMost(TIMEOUT).until(() -> "RELEASED".equals(statusOf(orderId)));
        long markerOrderId = nextId();

        send(Topics.COURIER_ASSIGNED, orderId,
                new CourierAssignedEvent(UUID.randomUUID(), 1L, orderId, 9001L, Instant.now(), 1L));
        send(Topics.ORDER_CREATED, orderId, orderCreated(UUID.randomUUID(), markerOrderId, userId, "1.00"));
        await().atMost(TIMEOUT).until(() -> "HELD".equals(statusOf(markerOrderId)));
        send(Topics.COURIER_ASSIGNED, orderId,
                new CourierAssignedEvent(UUID.randomUUID(), 2L, markerOrderId, 9001L, Instant.now(), 1L));

        await().atMost(TIMEOUT).until(() -> "CAPTURED".equals(statusOf(markerOrderId)));
        assertThat(statusOf(orderId)).isEqualTo("RELEASED");
        assertThat(operations(userId, "CAPTURE")).isEqualTo(1);
    }

    private long heldOrder(long userId, String amount) {
        long orderId = nextId();
        fund(userId, "500");
        send(Topics.ORDER_CREATED, orderId, orderCreated(UUID.randomUUID(), orderId, userId, amount));
        await().atMost(TIMEOUT).until(() -> "HELD".equals(statusOf(orderId)));
        return orderId;
    }

    private void send(String topic, long orderId, Object event) {
        kafkaTemplate.send(topic, Long.toString(orderId), event);
    }

    private KafkaTopicReader reader() {
        return new KafkaTopicReader(kafka.getBootstrapServers());
    }

    private boolean paymentExists(long orderId) {
        return jdbcClient.sql("SELECT count(*) FROM payments WHERE order_id = :orderId")
                .param("orderId", orderId)
                .query(Long.class)
                .single() > 0;
    }

    private String statusOf(long orderId) {
        return jdbcClient.sql("SELECT status FROM payments WHERE order_id = :orderId")
                .param("orderId", orderId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private static OrderCreatedEvent orderCreated(UUID eventId, long orderId, long userId, String amount) {
        return new OrderCreatedEvent(eventId, orderId, userId, 1L, new BigDecimal(amount), Instant.now(),
                List.of(), 1L);
    }
}
