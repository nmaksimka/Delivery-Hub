package com.deliveryhub.order.entity;

import org.junit.jupiter.api.Test;
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
        order.applyStatus(OrderStatus.CONFIRMED, NOW);

        assertThatThrownBy(() -> order.addItem(10L, "Pizza", 1, BigDecimal.TEN))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "CREATED,     CONFIRMED",
            "CREATED,     IN_DELIVERY",
            "CONFIRMED,   DELIVERED",
            "IN_DELIVERY, CANCELLED"
    })
    void movesForward(OrderStatus from, OrderStatus to) {
        Order order = orderIn(from);

        order.applyStatus(to, NOW);

        assertThat(order.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
            "CONFIRMED,   CREATED",
            "IN_DELIVERY, CONFIRMED",
            "DELIVERED,   CANCELLED",
            "CANCELLED,   CONFIRMED"
    })
    void rejectsBackwardAndPostFinalTransitions(OrderStatus from, OrderStatus to) {
        Order order = orderIn(from);

        assertThatThrownBy(() -> order.applyStatus(to, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(order.getStatus()).isEqualTo(from);
    }

    @Test
    void repeatedStatusIsIgnored() {
        Order order = orderIn(OrderStatus.DELIVERED);

        order.applyStatus(OrderStatus.DELIVERED, NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void knowsItsOwner() {
        Order order = Order.place(42L, 1L, NOW);

        assertThat(order.belongsTo(42L)).isTrue();
        assertThat(order.belongsTo(43L)).isFalse();
    }

    private static Order orderIn(OrderStatus status) {
        Order order = Order.place(42L, 1L, NOW);
        order.applyStatus(status, NOW);
        return order;
    }
}
