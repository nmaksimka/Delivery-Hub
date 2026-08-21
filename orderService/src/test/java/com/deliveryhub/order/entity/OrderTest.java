package com.deliveryhub.order.entity;

import com.deliveryhub.order.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-07-09T10:15:30Z");

    @Test
    void placedOrderIsCreatedWithZeroTotal() {
        Order order = Order.place(42L, 1L, NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("0");
        assertThat(order.getOrderItems()).isEmpty();
    }

    @Test
    void totalIsSumOfLineTotals() {
        Order order = Order.place(42L, 1L, NOW);

        order.addItem(10L, "Pizza", 2, new BigDecimal("12.50"));
        order.addItem(11L, "Cola", 3, new BigDecimal("3.00"));

        assertThat(order.getTotalAmount()).isEqualByComparingTo("34.00");
        assertThat(order.getOrderItems()).hasSize(2).allSatisfy(item -> assertThat(item.getOrder()).isSameAs(order));
    }

    @Test
    void rejectsZeroQuantity() {
        Order order = Order.place(42L, 1L, NOW);

        assertThatThrownBy(() -> order.addItem(10L, "Pizza", 0, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getOrderItems()).isEmpty();
    }

    @Test
    void itemsCannotBeAddedAfterConfirmation() {
        Order order = Order.place(42L, 1L, NOW);
        order.confirmPayment(NOW);

        assertThatThrownBy(() -> order.addItem(10L, "Pizza", 1, BigDecimal.TEN))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "CREATED,     CANCELLED",
            "PAYMENT_HELD, CANCELLED",
            "COURIER_ASSIGNED, CANCELLED",
            "CONFIRMED,   IN_DELIVERY",
            "CONFIRMED,   CANCELLED",
            "IN_DELIVERY, DELIVERED",
            "IN_DELIVERY, CANCELLED"
    })
    void manualChangeMovesDeliveryForwardOrCancels(OrderStatus from, OrderStatus to) {
        Order order = orderIn(from);

        assertThat(order.changeStatus(to, NOW)).isTrue();

        assertThat(order.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
            "CREATED,     IN_DELIVERY",
            "CREATED,     DELIVERED",
            "CREATED,     PAYMENT_HELD",
            "CREATED,     CONFIRMED",
            "PAYMENT_HELD, COURIER_ASSIGNED",
            "PAYMENT_HELD, CONFIRMED",
            "COURIER_ASSIGNED, CONFIRMED",
            "CONFIRMED,   DELIVERED",
            "CONFIRMED,   CREATED",
            "IN_DELIVERY, CONFIRMED",
            "DELIVERED,   CANCELLED",
            "CANCELLED,   CONFIRMED"
    })
    void rejectsSagaStepsJumpsBackwardAndPostFinalTransitions(OrderStatus from, OrderStatus to) {
        Order order = orderIn(from);

        assertThatThrownBy(() -> order.changeStatus(to, NOW))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(order.getStatus()).isEqualTo(from);
    }

    @Test
    void repeatedStatusIsNotAChange() {
        Order order = orderIn(OrderStatus.DELIVERED);

        assertThat(order.changeStatus(OrderStatus.DELIVERED, NOW)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "CONFIRMED,   IN_DELIVERY, IN_DELIVERY, true",
            "CONFIRMED,   DELIVERED,   DELIVERED,   true",
            "CREATED,     IN_DELIVERY, CREATED,     false",
            "PAYMENT_HELD, DELIVERED,  PAYMENT_HELD, false",
            "COURIER_ASSIGNED, IN_DELIVERY, COURIER_ASSIGNED, false",
            "CREATED,     COURIER_ASSIGNED, CREATED, false",
            "CONFIRMED,   CANCELLED,   CONFIRMED,   false",
            "PAYMENT_HELD, COURIER_ASSIGNED, COURIER_ASSIGNED, true",
            "IN_DELIVERY, CONFIRMED,   IN_DELIVERY, false",
            "DELIVERED,   CANCELLED,   DELIVERED,   false",
            "CANCELLED,   IN_DELIVERY, CANCELLED,   false"
    })
    void followsDeliveryOnlyForward(OrderStatus from, OrderStatus reported, OrderStatus expected, boolean changed) {
        Order order = orderIn(from);

        assertThat(order.followDelivery(reported, NOW)).isEqualTo(changed);

        assertThat(order.getStatus()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"CREATED", "PAYMENT_HELD", "COURIER_ASSIGNED", "CONFIRMED"})
    void customerCancelsOrderThatIsNotOnItsWay(OrderStatus from) {
        Order order = orderIn(from);

        assertThat(order.cancelByCustomer(NOW)).isTrue();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.CUSTOMER);
    }

    @Test
    void sagaMovesOrderFromPaymentToConfirmation() {
        Order order = Order.place(42L, 1L, NOW);

        assertThat(order.markPaymentHeld(NOW)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_HELD);
        assertThat(order.markCourierAssigned(NOW)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COURIER_ASSIGNED);
        assertThat(order.confirmPayment(NOW)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void lateSagaStepDoesNotMoveOrderBack() {
        Order order = orderIn(OrderStatus.CONFIRMED);

        assertThat(order.markPaymentHeld(NOW)).isFalse();
        assertThat(order.markCourierAssigned(NOW)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void sagaStepDoesNotReviveCancelledOrder() {
        Order order = Order.place(42L, 1L, NOW);
        order.cancel(CancellationReason.PAYMENT_TIMEOUT, NOW);

        assertThat(order.markPaymentHeld(NOW)).isFalse();
        assertThat(order.confirmPayment(NOW)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.PAYMENT_TIMEOUT);
    }

    @Test
    void secondCancellationKeepsFirstReason() {
        Order order = Order.place(42L, 1L, NOW);

        assertThat(order.cancel(CancellationReason.PAYMENT_REJECTED, NOW)).isTrue();
        assertThat(order.cancel(CancellationReason.ADMIN, NOW.plusSeconds(5))).isFalse();

        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.PAYMENT_REJECTED);
    }

    @Test
    void deliveredOrderCannotBeCancelled() {
        Order order = orderIn(OrderStatus.DELIVERED);

        assertThatThrownBy(() -> order.cancel(CancellationReason.ADMIN, NOW))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void refundIsRecordedOnceAndOnlyForCancelledOrder() {
        Order order = orderIn(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> order.recordRefund(NOW)).isInstanceOf(InvalidStatusTransitionException.class);

        order.cancel(CancellationReason.CUSTOMER, NOW);

        assertThat(order.recordRefund(NOW)).isTrue();
        assertThat(order.recordRefund(NOW.plusSeconds(1))).isFalse();
        assertThat(order.getRefundedAt()).isEqualTo(NOW);
    }

    @Test
    void stuckOrderIsDetectedOnlyInAwaitedStatus() {
        Order order = Order.place(42L, 1L, NOW);

        assertThat(order.isStuckIn(OrderStatus.CREATED, NOW.plusSeconds(60))).isTrue();
        assertThat(order.isStuckIn(OrderStatus.CREATED, NOW)).isFalse();
        assertThat(order.isStuckIn(OrderStatus.PAYMENT_HELD, NOW.plusSeconds(60))).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"IN_DELIVERY", "DELIVERED", "CANCELLED"})
    void customerCannotCancelOrderThatLeftRestaurantOrIsFinished(OrderStatus from) {
        Order order = orderIn(from);

        assertThatThrownBy(() -> order.cancelByCustomer(NOW))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(order.getStatus()).isEqualTo(from);
    }

    @Test
    void numbersOwnEventsWithoutGaps() {
        Order order = Order.place(42L, 1L, NOW);

        assertThat(order.nextEventSequence()).isEqualTo(1);
        assertThat(order.nextEventSequence()).isEqualTo(2);
    }

    @Test
    void acceptsDeliveryEventsOnlyInGrowingOrder() {
        Order order = Order.place(42L, 1L, NOW);

        assertThat(order.acceptDeliveryEvent(2)).isTrue();
        assertThat(order.acceptDeliveryEvent(2)).isFalse();
        assertThat(order.acceptDeliveryEvent(1)).isFalse();
        assertThat(order.acceptDeliveryEvent(5)).isTrue();
    }

    @Test
    void eventWithoutSequenceIsAlwaysAccepted() {
        Order order = Order.place(42L, 1L, NOW);
        order.acceptDeliveryEvent(7);

        assertThat(order.acceptDeliveryEvent(0)).isTrue();
        assertThat(order.acceptDeliveryEvent(7)).isFalse();
    }

    @Test
    void knowsItsOwner() {
        Order order = Order.place(42L, 1L, NOW);

        assertThat(order.belongsTo(42L)).isTrue();
        assertThat(order.belongsTo(43L)).isFalse();
    }

    @Test
    void discountReducesTotalAndKeepsSubtotal() {
        Order order = Order.place(42L, 1L, NOW);
        order.addItem(10L, "Pizza", 2, new BigDecimal("12.50"));

        order.applyDiscount("SPRING10", new BigDecimal("2.50"));

        assertThat(order.getTotalAmount()).isEqualByComparingTo("22.50");
        assertThat(order.getDiscount()).isEqualByComparingTo("2.50");
        assertThat(order.subtotal()).isEqualByComparingTo("25.00");
        assertThat(order.getPromoCode()).isEqualTo("SPRING10");
        assertThat(order.hasPromo()).isTrue();
    }

    @Test
    void orderWithoutPromoHasZeroDiscount() {
        Order order = Order.place(42L, 1L, NOW);
        order.addItem(10L, "Pizza", 1, BigDecimal.TEN);

        assertThat(order.getDiscount()).isEqualByComparingTo("0");
        assertThat(order.getPromoCode()).isNull();
        assertThat(order.hasPromo()).isFalse();
        assertThat(order.subtotal()).isEqualByComparingTo("10");
    }

    @Test
    void discountCannotExceedTotalOrBeAppliedTwiceOrAfterCreation() {
        Order order = Order.place(42L, 1L, NOW);
        order.addItem(10L, "Pizza", 1, BigDecimal.TEN);

        assertThatThrownBy(() -> order.applyDiscount("BIG", new BigDecimal("10.01")))
                .isInstanceOf(IllegalArgumentException.class);
        order.applyDiscount("TEN", BigDecimal.TEN);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> order.applyDiscount("TWICE", BigDecimal.ONE))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> orderIn(OrderStatus.CONFIRMED).applyDiscount("LATE", BigDecimal.ZERO))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deliveryFeeGoesToTotalButPromoDiscountsOnlyDishes() {
        Order order = Order.place(42L, 1L, NOW);
        order.addItem(10L, "Pizza", 2, new BigDecimal("300.00"));

        order.deliverTo(destination(), new BigDecimal("190.00"), 12, breakdown());
        order.applyDiscount("ALL", new BigDecimal("600.00"));

        assertThat(order.getTotalAmount()).isEqualByComparingTo("190.00");
        assertThat(order.subtotal()).isEqualByComparingTo("600.00");
        assertThat(order.getDeliveryFee()).isEqualByComparingTo("190.00");
        assertThat(order.getEtaMinutes()).isEqualTo(12);
        assertThat(order.getDestination().line()).isEqualTo("Томск, Ленина, 12");
        assertThatThrownBy(() -> Order.place(42L, 1L, NOW).applyDiscount("BIG", BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deliveryIsSetOnceAndOnlyForNewOrder() {
        Order order = Order.place(42L, 1L, NOW);
        order.deliverTo(destination(), BigDecimal.TEN, 10, breakdown());

        assertThatThrownBy(() -> order.deliverTo(destination(), BigDecimal.TEN, 10, breakdown()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> orderIn(OrderStatus.CONFIRMED).deliverTo(destination(), BigDecimal.TEN, 10, breakdown()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Order.place(42L, 1L, NOW).deliverTo(destination(), BigDecimal.ONE.negate(), 10, breakdown()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DeliveryFeeBreakdown breakdown() {
        return new DeliveryFeeBreakdown(2793, BigDecimal.ZERO, "OSRM");
    }

    private static DeliveryDestination destination() {
        return new DeliveryDestination(5L, "Томск, Ленина, 12", null, new BigDecimal("56.484645"), new BigDecimal("84.947649"));
    }

    private static Order orderIn(OrderStatus status) {
        Order order = Order.place(42L, 1L, NOW);
        ReflectionTestUtils.setField(order, "status", status);
        return order;
    }
}
