package com.deliveryhub.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class})
class PaymentApiIT extends PaymentApiSupport {

    private static final int PARALLEL_REQUESTS = 10;

    @Test
    void holdReservesMoneyOfOrder() {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "500");

        MvcTestResult result = hold(orderId, userId, "120.40");

        assertThat(result).hasStatusOk();
        assertThat(json(result, "$.status")).isEqualTo("HELD");
        assertThat(json(result, "$.provider")).isEqualTo("demo");
        assertThat(available(userId)).isEqualByComparingTo("379.60");
        assertThat(held(userId)).isEqualByComparingTo("120.40");
    }

    @Test
    void repeatedHoldOfSameOrderDoesNotMoveMoneyAgain() {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "500");

        hold(orderId, userId, "100").assertThat().hasStatusOk();
        MvcTestResult retry = hold(orderId, userId, "100.00");

        assertThat(retry).hasStatusOk();
        assertThat(json(retry, "$.status")).isEqualTo("HELD");
        assertThat(available(userId)).isEqualByComparingTo("400.00");
        assertThat(operations(userId, "HOLD")).isEqualTo(1);
    }

    @Test
    void holdOfSameOrderWithAnotherAmountIsConflict() {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "500");
        hold(orderId, userId, "100").assertThat().hasStatusOk();

        MvcTestResult other = hold(orderId, userId, "150");

        assertThat(other).hasStatus(409);
        assertThat(json(other, "$.code")).isEqualTo("PAYMENT_ORDER_MISMATCH");
        assertThat(available(userId)).isEqualByComparingTo("400.00");
    }

    @Test
    void holdWithoutEnoughMoneyIsRejectedAndRemembered() {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "50");

        MvcTestResult rejected = hold(orderId, userId, "50.01");
        fund(userId, "100");
        MvcTestResult retry = hold(orderId, userId, "50.01");

        assertThat(rejected).hasStatus(422);
        assertThat(json(rejected, "$.code")).isEqualTo("PAYMENT_INSUFFICIENT_FUNDS");
        assertThat(retry).hasStatus(422);
        assertThat(json(retry, "$.code")).isEqualTo("PAYMENT_INSUFFICIENT_FUNDS");
        assertThat(available(userId)).isEqualByComparingTo("150.00");
        assertThat(operations(userId, "HOLD")).isZero();
        assertThat(paymentStatus(orderId)).isEqualTo("REJECTED");
    }

    @Test
    void captureIsIdempotentAndTakesHeldMoney() {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "300");
        hold(orderId, userId, "200").assertThat().hasStatusOk();

        MvcTestResult first = operation(orderId, "capture");
        MvcTestResult retry = operation(orderId, "capture");

        assertThat(first).hasStatusOk();
        assertThat(json(retry, "$.status")).isEqualTo("CAPTURED");
        assertThat(available(userId)).isEqualByComparingTo("100.00");
        assertThat(held(userId)).isEqualByComparingTo("0");
        assertThat(operations(userId, "CAPTURE")).isEqualTo(1);
    }

    @Test
    void releaseReturnsHeldMoneyOnce() {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "300");
        hold(orderId, userId, "200").assertThat().hasStatusOk();

        operation(orderId, "release").assertThat().hasStatusOk();
        operation(orderId, "release").assertThat().hasStatusOk();

        assertThat(available(userId)).isEqualByComparingTo("300.00");
        assertThat(held(userId)).isEqualByComparingTo("0");
        assertThat(operations(userId, "RELEASE")).isEqualTo(1);
        assertThat(paymentStatus(orderId)).isEqualTo("RELEASED");
    }

    @Test
    void refundAfterCaptureReturnsMoneyOnce() {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "300");
        hold(orderId, userId, "200").assertThat().hasStatusOk();
        operation(orderId, "capture").assertThat().hasStatusOk();

        operation(orderId, "refund").assertThat().hasStatusOk();
        operation(orderId, "refund").assertThat().hasStatusOk();

        assertThat(available(userId)).isEqualByComparingTo("300.00");
        assertThat(operations(userId, "REFUND")).isEqualTo(1);
    }

    @Test
    void wrongTransitionsAreConflicts() {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "300");
        hold(orderId, userId, "200").assertThat().hasStatusOk();

        MvcTestResult refundOfHeld = operation(orderId, "refund");
        operation(orderId, "capture").assertThat().hasStatusOk();
        MvcTestResult releaseOfCaptured = operation(orderId, "release");

        assertThat(refundOfHeld).hasStatus(409);
        assertThat(json(refundOfHeld, "$.code")).isEqualTo("PAYMENT_INVALID_STATE");
        assertThat(releaseOfCaptured).hasStatus(409);
        assertThat(available(userId)).isEqualByComparingTo("100.00");
    }

    @Test
    void operationOnUnknownOrderIsNotFound() {
        MvcTestResult result = operation(nextId(), "capture");

        assertThat(result).hasStatus(404);
        assertThat(json(result, "$.code")).isEqualTo("PAYMENT_NOT_FOUND");
    }

    @Test
    void parallelHoldsOnOneWalletNeverOverdrawIt() throws Exception {
        long userId = nextId();
        fund(userId, "100");
        List<Long> orders = IntStream.range(0, PARALLEL_REQUESTS).mapToObj(i -> nextId()).toList();

        List<Integer> statuses = inParallel(orders.stream()
                .<Callable<Integer>>map(orderId -> () -> hold(orderId, userId, "30").getResponse().getStatus())
                .toList());

        assertThat(statuses).filteredOn(status -> status == 200).hasSize(3);
        assertThat(statuses).filteredOn(status -> status == 422).hasSize(PARALLEL_REQUESTS - 3);
        assertThat(available(userId)).isEqualByComparingTo("10.00");
        assertThat(held(userId)).isEqualByComparingTo("90.00");
        assertThat(operations(userId, "HOLD")).isEqualTo(3);
    }

    @Test
    void parallelHoldsOfSameOrderHoldMoneyOnce() throws Exception {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "1000");

        List<Integer> statuses = inParallel(IntStream.range(0, PARALLEL_REQUESTS)
                .<Callable<Integer>>mapToObj(i -> () -> hold(orderId, userId, "100").getResponse().getStatus())
                .toList());

        assertThat(statuses).containsOnly(200);
        assertThat(available(userId)).isEqualByComparingTo("900.00");
        assertThat(held(userId)).isEqualByComparingTo("100.00");
        assertThat(operations(userId, "HOLD")).isEqualTo(1);
    }

    @Test
    void parallelCapturesTakeMoneyOnce() throws Exception {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "500");
        hold(orderId, userId, "200").assertThat().hasStatusOk();

        List<Integer> statuses = inParallel(IntStream.range(0, PARALLEL_REQUESTS)
                .<Callable<Integer>>mapToObj(i -> () -> operation(orderId, "capture").getResponse().getStatus())
                .toList());

        assertThat(statuses).containsOnly(200);
        assertThat(available(userId)).isEqualByComparingTo("300.00");
        assertThat(held(userId)).isEqualByComparingTo("0");
        assertThat(operations(userId, "CAPTURE")).isEqualTo(1);
    }

    @Test
    void ownerAndAdminSeeOrderPaymentOthersDoNot() {
        long userId = nextId();
        long orderId = nextId();
        fund(userId, "100");
        hold(orderId, userId, "40").assertThat().hasStatusOk();

        MvcTestResult owner = orderPayment(orderId, userId, "USER");
        MvcTestResult stranger = orderPayment(orderId, nextId(), "USER");
        MvcTestResult admin = orderPayment(orderId, nextId(), "ADMIN");

        assertThat(owner).hasStatusOk();
        assertThat(amountAt(owner, "$.amount")).isEqualByComparingTo("40.00");
        assertThat(stranger).hasStatus(404);
        assertThat(admin).hasStatusOk();
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
