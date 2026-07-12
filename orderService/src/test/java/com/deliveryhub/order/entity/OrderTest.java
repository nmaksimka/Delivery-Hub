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
        order.changeStatus(OrderStatus.CONFIRMED, NOW);

        assertThatThrownBy(() -> order.addItem(10L, "Pizza", 1, BigDecimal.TEN))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "CREATED,     CONFIRMED",
            "CREATED,     CANCELLED",
            "CONFIRMED,   IN_DELIVERY",
            "CONFIRMED,   CANCELLED",
            "IN_DELIVERY, DELIVERED",
            "IN_DELIVERY, CANCELLED"
    })
    void movesOneStepForwardOrCancels(OrderStatus from, OrderStatus to) {
        Order order = orderIn(from);

        assertThat(order.changeStatus(to, NOW)).isTrue();

        assertThat(order.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
            "CREATED,     IN_DELIVERY",
            "CREATED,     DELIVERED",
            "CONFIRMED,   DELIVERED",
            "CONFIRMED,   CREATED",
            "IN_DELIVERY, CONFIRMED",
            "DELIVERED,   CANCELLED",
            "CANCELLED,   CONFIRMED"
    })
    void rejectsJumpsBackwardAndPostFinalTransitions(OrderStatus from, OrderStatus to) {
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
            "CREATED,     IN_DELIVERY, IN_DELIVERY, true",
            "CREATED,     DELIVERED,   DELIVERED,   true",
            "CONFIRMED,   CANCELLED,   CANCELLED,   true",
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
    @CsvSource({"CREATED", "CONFIRMED"})
    void customerCancelsOrderThatIsNotOnItsWay(OrderStatus from) {
        Order order = orderIn(from);

        order.cancelByCustomer(NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
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

    private static Order orderIn(OrderStatus status) {
        Order order = Order.place(42L, 1L, NOW);
        ReflectionTestUtils.setField(order, "status", status);
        return order;
    }
}
