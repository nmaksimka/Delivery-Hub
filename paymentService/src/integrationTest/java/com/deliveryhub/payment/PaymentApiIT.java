package com.deliveryhub.payment;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.CourierAssignedEvent;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.payment.application.PaymentSaga;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class, KafkaContainerConfig.class})
class PaymentApiIT extends PaymentApiSupport {

    private static final int PARALLEL_REQUESTS = 10;

    @Autowired
    private PaymentSaga saga;

    @Test
    void createdOrderHoldsMoneyAndAnnouncesIt() {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "500");

        saga.onOrderCreated(orderCreated(orderId, userId, "120.40"));

        assertThat(paymentStatus(orderId)).isEqualTo("HELD");
        assertThat(available(userId)).isEqualByComparingTo("379.60");
        assertThat(held(userId)).isEqualByComparingTo("120.40");
        assertThat(announced(orderId, Topics.PAYMENT_HELD)).isEqualTo(1);
    }

    @Test
    void repeatedOrderCreatedRepublishesHeldWithoutMovingMoney() {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "500");

        saga.onOrderCreated(orderCreated(orderId, userId, "100"));
        saga.onOrderCreated(orderCreated(orderId, userId, "100.00"));

        assertThat(available(userId)).isEqualByComparingTo("400.00");
        assertThat(operations(userId, "HOLD")).isEqualTo(1);
        assertThat(announced(orderId, Topics.PAYMENT_HELD)).isEqualTo(2);
    }

    @Test
    void sameOrderWithAnotherAmountIsRejectedAndKeepsMoney() {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "500");
        saga.onOrderCreated(orderCreated(orderId, userId, "100"));

        saga.onOrderCreated(orderCreated(orderId, userId, "150"));

        assertThat(paymentStatus(orderId)).isEqualTo("HELD");
        assertThat(available(userId)).isEqualByComparingTo("400.00");
        assertThat(announced(orderId, Topics.PAYMENT_REJECTED)).isEqualTo(1);
    }

    @Test
    void declinedHoldIsRememberedAndRepublished() {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "50");

        saga.onOrderCreated(orderCreated(orderId, userId, "50.01"));
        fund(userId, "100");
        saga.onOrderCreated(orderCreated(orderId, userId, "50.01"));

        assertThat(paymentStatus(orderId)).isEqualTo("REJECTED");
        assertThat(available(userId)).isEqualByComparingTo("150.00");
        assertThat(operations(userId, "HOLD")).isZero();
        assertThat(announced(orderId, Topics.PAYMENT_REJECTED)).isEqualTo(2);
    }

    @Test
    void freeOrderIsHeldCapturedAndRefundedWithoutTouchingWallet() {
        long userId = nextId();
        long orderId = nextId();

        saga.onOrderCreated(orderCreated(orderId, userId, "0.00"));
        saga.onCourierAssigned(courierAssigned(orderId));
        saga.onOrderCancelled(orderCancelled(orderId, userId));

        assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(announced(orderId, Topics.PAYMENT_HELD)).isEqualTo(1);
        assertThat(announced(orderId, Topics.PAYMENT_CAPTURED)).isEqualTo(1);
        assertThat(announced(orderId, Topics.PAYMENT_REFUNDED)).isEqualTo(1);
        assertThat(walletOperations(userId)).isZero();
    }

    @Test
    void repeatedCourierAssignmentCapturesOnce() {
        long userId = nextId();
        long orderId = heldOrder(userId, "300", "200");

        saga.onCourierAssigned(courierAssigned(orderId));
        saga.onCourierAssigned(courierAssigned(orderId));

        assertThat(paymentStatus(orderId)).isEqualTo("CAPTURED");
        assertThat(available(userId)).isEqualByComparingTo("100.00");
        assertThat(held(userId)).isEqualByComparingTo("0");
        assertThat(operations(userId, "CAPTURE")).isEqualTo(1);
    }

    @Test
    void repeatedCancellationReleasesHoldOnce() {
        long userId = nextId();
        long orderId = heldOrder(userId, "300", "200");

        saga.onOrderCancelled(orderCancelled(orderId, userId));
        saga.onOrderCancelled(orderCancelled(orderId, userId));

        assertThat(paymentStatus(orderId)).isEqualTo("RELEASED");
        assertThat(available(userId)).isEqualByComparingTo("300.00");
        assertThat(operations(userId, "RELEASE")).isEqualTo(1);
    }

    @Test
    void repeatedCancellationAfterCaptureRefundsOnce() {
        long userId = nextId();
        long orderId = heldOrder(userId, "300", "200");
        saga.onCourierAssigned(courierAssigned(orderId));

        saga.onOrderCancelled(orderCancelled(orderId, userId));
        saga.onOrderCancelled(orderCancelled(orderId, userId));

        assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(available(userId)).isEqualByComparingTo("300.00");
        assertThat(operations(userId, "REFUND")).isEqualTo(1);
    }

    @Test
    void internalPaymentEndpointsAreGone() {
        long orderId = heldOrder(nextId(), "300", "200");

        MvcTestResult capture = mvc.post().uri("/internal/payments/{orderId}/capture", orderId).exchange();
        MvcTestResult hold = mvc.post().uri("/internal/payments/{orderId}/hold", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": 1, \"amount\": 10}")
                .exchange();

        assertThat(capture).hasStatus(404);
        assertThat(hold).hasStatus(404);
        assertThat(paymentStatus(orderId)).isEqualTo("HELD");
    }

    @Test
    void parallelOrdersOnOneWalletNeverOverdrawIt() throws Exception {
        long userId = nextId();
        fund(userId, "100");
        List<Long> orders = IntStream.range(0, PARALLEL_REQUESTS).mapToObj(i -> nextId()).toList();

        inParallel(orders.stream()
                .<Callable<Void>>map(orderId -> () -> {
                    saga.onOrderCreated(orderCreated(orderId, userId, "30"));
                    return null;
                })
                .toList());

        assertThat(orders).filteredOn(orderId -> "HELD".equals(paymentStatus(orderId))).hasSize(3);
        assertThat(orders).filteredOn(orderId -> "REJECTED".equals(paymentStatus(orderId)))
                .hasSize(PARALLEL_REQUESTS - 3);
        assertThat(available(userId)).isEqualByComparingTo("10.00");
        assertThat(held(userId)).isEqualByComparingTo("90.00");
        assertThat(operations(userId, "HOLD")).isEqualTo(3);
    }

    @Test
    void parallelDeliveriesOfSameEventHoldMoneyOnce() throws Exception {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "1000");
        OrderCreatedEvent event = orderCreated(orderId, userId, "100");

        inParallel(IntStream.range(0, PARALLEL_REQUESTS)
                .<Callable<Void>>mapToObj(i -> () -> {
                    saga.onOrderCreated(event);
                    return null;
                })
                .toList());

        assertThat(available(userId)).isEqualByComparingTo("900.00");
        assertThat(held(userId)).isEqualByComparingTo("100.00");
        assertThat(operations(userId, "HOLD")).isEqualTo(1);
        assertThat(announced(orderId, Topics.PAYMENT_HELD)).isEqualTo(1);
    }

    @Test
    void parallelCourierAssignmentsTakeMoneyOnce() throws Exception {
        long userId = nextId();
        long orderId = heldOrder(userId, "500", "200");

        inParallel(IntStream.range(0, PARALLEL_REQUESTS)
                .<Callable<Void>>mapToObj(i -> () -> {
                    saga.onCourierAssigned(courierAssigned(orderId));
                    return null;
                })
                .toList());

        assertThat(available(userId)).isEqualByComparingTo("300.00");
        assertThat(held(userId)).isEqualByComparingTo("0");
        assertThat(operations(userId, "CAPTURE")).isEqualTo(1);
        assertThat(announced(orderId, Topics.PAYMENT_CAPTURED)).isEqualTo(1);
    }

    @Test
    void ownerAndAdminSeeOrderPaymentOthersDoNot() {
        long userId = nextId();
        long orderId = heldOrder(userId, "100", "40");

        MvcTestResult owner = orderPayment(orderId, userId, "USER");
        MvcTestResult stranger = orderPayment(orderId, nextId(), "USER");
        MvcTestResult admin = orderPayment(orderId, nextId(), "ADMIN");

        assertThat(owner).hasStatusOk();
        assertThat(amountAt(owner, "$.amount")).isEqualByComparingTo("40.00");
        assertThat(stranger).hasStatus(404);
        assertThat(admin).hasStatusOk();
    }

    private long heldOrder(long userId, String funds, String amount) {
        long orderId = nextId();
        fund(userId, funds);
        saga.onOrderCreated(orderCreated(orderId, userId, amount));
        assertThat(paymentStatus(orderId)).isEqualTo("HELD");
        return orderId;
    }

    private MvcTestResult orderPayment(long orderId, long viewerId, String role) {
        return mvc.get().uri("/api/payments/order/{orderId}", orderId)
                .header("X-User-Id", String.valueOf(viewerId))
                .header("X-User-Role", role)
                .exchange();
    }

    private String paymentStatus(long orderId) {
        return jdbcClient.sql("SELECT status FROM payments WHERE order_id = :orderId")
                .param("orderId", orderId)
                .query(String.class)
                .single();
    }

    private long announced(long orderId, String topic) {
        return jdbcClient.sql("SELECT count(*) FROM outbox_events WHERE aggregate_id = :orderId AND event_type = :topic")
                .param("orderId", Long.toString(orderId))
                .param("topic", topic)
                .query(Long.class)
                .single();
    }

    private long walletOperations(long userId) {
        return jdbcClient.sql("""
                        SELECT count(*) FROM wallet_operations o JOIN wallets w ON w.id = o.wallet_id
                        WHERE w.user_id = :userId
                        """)
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    private static OrderCreatedEvent orderCreated(long orderId, long userId, String amount) {
        return new OrderCreatedEvent(UUID.randomUUID(), orderId, userId, 1L, new BigDecimal(amount), Instant.now(),
                List.of(), 1L, null, null);
    }

    private static CourierAssignedEvent courierAssigned(long orderId) {
        return new CourierAssignedEvent(UUID.randomUUID(), 1L, orderId, 9001L, Instant.now(), 1L);
    }

    private static OrderCancelledEvent orderCancelled(long orderId, long userId) {
        return new OrderCancelledEvent(UUID.randomUUID(), orderId, userId, Instant.now(), 2L);
    }

    private static <T> List<T> inParallel(List<Callable<T>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
            List<Future<T>> futures = tasks.stream()
                    .map(task -> executor.submit(() -> {
                        start.await();
                        return task.call();
                    }))
                    .toList();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }
}
