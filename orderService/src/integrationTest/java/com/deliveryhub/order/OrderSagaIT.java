package com.deliveryhub.order;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.CourierAssignedEvent;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
import com.deliveryhub.contracts.events.PaymentCapturedEvent;
import com.deliveryhub.contracts.events.PaymentHeldEvent;
import com.deliveryhub.contracts.events.PaymentRefundedEvent;
import com.deliveryhub.contracts.events.PaymentRejectedEvent;
import com.deliveryhub.contracts.events.PaymentReleasedEvent;
import com.deliveryhub.order.client.MenuQuote;
import com.deliveryhub.order.client.RestaurantCatalog;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.dto.CreateOrderRequest;
import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.dto.OrderItemRequest;
import com.deliveryhub.order.entity.CancellationReason;
import com.deliveryhub.order.entity.OrderStatus;
import com.deliveryhub.order.security.CurrentUser;
import com.deliveryhub.order.service.OrderService;
import com.deliveryhub.order.service.SagaTimeouts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class OrderSagaIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final long USER_ID = 42L;
    private static final BigDecimal AMOUNT = new BigDecimal("25.00");

    @Autowired
    private OrderService orderService;
    @Autowired
    private SagaTimeouts sagaTimeouts;
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private KafkaContainer kafka;
    @Autowired
    private JdbcClient jdbcClient;
    @MockitoBean
    private RestaurantCatalog restaurantCatalog;

    @BeforeEach
    void stubRestaurantService() {
        when(restaurantCatalog.quote(eq(1L), any())).thenReturn(new MenuQuote(
                new RestaurantDto(1L, "Pizzeria", true),
                Map.of(10L, new MenuItemDto(10L, "Pizza", new BigDecimal("12.50"), true))));
    }

    @Test
    void heldPaymentCourierAndCaptureConfirmOrder() {
        long orderId = placeOrder();

        send(Topics.PAYMENT_HELD, orderId, held(UUID.randomUUID(), orderId));
        await().atMost(TIMEOUT).until(() -> view(orderId).status() == OrderStatus.PAYMENT_HELD);
        send(Topics.COURIER_ASSIGNED, orderId,
                new CourierAssignedEvent(UUID.randomUUID(), 3L, orderId, 9001L, Instant.now(), 1L));
        await().atMost(TIMEOUT).until(() -> view(orderId).status() == OrderStatus.COURIER_ASSIGNED);
        send(Topics.PAYMENT_CAPTURED, orderId,
                new PaymentCapturedEvent(UUID.randomUUID(), orderId, USER_ID, AMOUNT, Instant.now()));

        await().atMost(TIMEOUT).until(() -> view(orderId).status() == OrderStatus.CONFIRMED);
        assertThat(view(orderId).cancellationReason()).isNull();
    }

    @Test
    void rejectedPaymentCancelsOrderAndTellsOthers() {
        long orderId = placeOrder();

        send(Topics.PAYMENT_REJECTED, orderId, new PaymentRejectedEvent(UUID.randomUUID(), orderId, USER_ID, AMOUNT,
                "PAYMENT_INSUFFICIENT_FUNDS", Instant.now()));

        await().atMost(TIMEOUT).until(() -> view(orderId).status() == OrderStatus.CANCELLED);
        assertThat(view(orderId).cancellationReason()).isEqualTo(CancellationReason.PAYMENT_REJECTED);
        assertThat(cancelledEvent(orderId, 2L)).isPresent();
    }

    @Test
    void releasedHoldAfterDeliveryRejectionCancelsOrder() {
        long orderId = placeOrder();
        send(Topics.PAYMENT_HELD, orderId, held(UUID.randomUUID(), orderId));
        await().atMost(TIMEOUT).until(() -> view(orderId).status() == OrderStatus.PAYMENT_HELD);

        send(Topics.PAYMENT_RELEASED, orderId, new PaymentReleasedEvent(UUID.randomUUID(), orderId, USER_ID, AMOUNT,
                Instant.now(), "DELIVERY_REJECTED"));

        await().atMost(TIMEOUT).until(() -> view(orderId).status() == OrderStatus.CANCELLED);
        assertThat(view(orderId).cancellationReason()).isEqualTo(CancellationReason.DELIVERY_REJECTED);
    }

    @Test
    void refundIsVisibleOnCancelledOrder() {
        long orderId = placeOrder();
        orderService.cancelOrder(orderId, new CurrentUser(USER_ID, "USER"));

        send(Topics.PAYMENT_REFUNDED, orderId,
                new PaymentRefundedEvent(UUID.randomUUID(), orderId, USER_ID, AMOUNT, Instant.now()));

        await().atMost(TIMEOUT).until(() -> view(orderId).refundedAt() != null);
        assertThat(view(orderId).cancellationReason()).isEqualTo(CancellationReason.CUSTOMER);
    }

    @Test
    void timeoutWinsOverLatePaymentAndAsksToReleaseItAgain() {
        long orderId = placeOrder();
        backdate(orderId, Duration.ofMinutes(3));

        sagaTimeouts.cancelStuckOrders();

        assertThat(view(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(view(orderId).cancellationReason()).isEqualTo(CancellationReason.PAYMENT_TIMEOUT);
        assertThat(cancelledEvent(orderId, 2L)).isPresent();

        send(Topics.PAYMENT_HELD, orderId, held(UUID.randomUUID(), orderId));

        assertThat(cancelledEvent(orderId, 3L)).isPresent();
        assertThat(view(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(view(orderId).cancellationReason()).isEqualTo(CancellationReason.PAYMENT_TIMEOUT);
    }

    @Test
    void paymentThatArrivedInTimeIsNotTimedOutAsUnpaid() {
        long orderId = placeOrder();
        send(Topics.PAYMENT_HELD, orderId, held(UUID.randomUUID(), orderId));
        await().atMost(TIMEOUT).until(() -> view(orderId).status() == OrderStatus.PAYMENT_HELD);
        backdate(orderId, Duration.ofMinutes(3));

        sagaTimeouts.cancelStuckOrders();

        assertThat(view(orderId).status()).isEqualTo(OrderStatus.PAYMENT_HELD);
    }

    @Test
    void duplicateLatePaymentRequestsCompensationOnce() {
        long orderId = placeOrder();
        orderService.cancelOrder(orderId, new CurrentUser(USER_ID, "USER"));
        PaymentHeldEvent late = held(UUID.randomUUID(), orderId);
        long markerOrderId = placeOrder();

        send(Topics.PAYMENT_HELD, orderId, late);
        send(Topics.PAYMENT_HELD, orderId, late);
        send(Topics.PAYMENT_HELD, orderId, held(UUID.randomUUID(), markerOrderId));

        await().atMost(TIMEOUT).until(() -> view(markerOrderId).status() == OrderStatus.PAYMENT_HELD);
        assertThat(cancellationsInOutbox(orderId)).isEqualTo(2);
    }

    private long placeOrder() {
        return orderService.createOrder(
                new CreateOrderRequest(1L, List.of(new OrderItemRequest(10L, 2))), USER_ID).id();
    }

    private OrderDto view(long orderId) {
        return orderService.getOrderById(orderId, new CurrentUser(USER_ID, "USER"));
    }

    private void send(String topic, long orderId, Object event) {
        kafkaTemplate.send(topic, Long.toString(orderId), event);
    }

    private Optional<OrderCancelledEvent> cancelledEvent(long orderId, long sequence) {
        return new KafkaTopicReader(kafka.getBootstrapServers()).findFirst(Topics.ORDER_CANCELLED,
                OrderCancelledEvent.class, event -> event.getOrderId() == orderId && event.getSequence() == sequence);
    }

    private void backdate(long orderId, Duration age) {
        jdbcClient.sql("UPDATE orders SET updated_at = updated_at - make_interval(secs => :seconds) WHERE id = :id")
                .param("seconds", age.toSeconds())
                .param("id", orderId)
                .update();
    }

    private long cancellationsInOutbox(long orderId) {
        return jdbcClient.sql("SELECT count(*) FROM outbox_events WHERE aggregate_id = :id AND event_type = :type")
                .param("id", Long.toString(orderId))
                .param("type", Topics.ORDER_CANCELLED)
                .query(Long.class)
                .single();
    }

    private static PaymentHeldEvent held(UUID eventId, long orderId) {
        return new PaymentHeldEvent(eventId, orderId, USER_ID, AMOUNT, "demo", Instant.now(), null, null);
    }
}
