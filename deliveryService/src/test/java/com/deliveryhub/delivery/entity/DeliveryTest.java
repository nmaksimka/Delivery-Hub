package com.deliveryhub.delivery.entity;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryTest {

    private static final Instant NOW = Instant.parse("2026-07-09T10:15:30Z");

    @Test
    void newDeliveryIsAssigned() {
        assertThat(Delivery.forOrder(100L, NOW).getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
    }

    @ParameterizedTest
    @CsvSource({
            "ASSIGNED,   PICKED_UP",
            "ASSIGNED,   DELIVERED",
            "PICKED_UP,  IN_TRANSIT",
            "IN_TRANSIT, DELIVERED",
            "IN_TRANSIT, CANCELLED"
    })
    void movesForward(DeliveryStatus from, DeliveryStatus to) {
        Delivery delivery = deliveryIn(from);

        delivery.changeStatus(to, NOW);

        assertThat(delivery.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
            "PICKED_UP,  ASSIGNED",
            "IN_TRANSIT, PICKED_UP",
            "DELIVERED,  IN_TRANSIT",
            "DELIVERED,  CANCELLED",
            "CANCELLED,  ASSIGNED"
    })
    void rejectsBackwardAndPostFinalTransitions(DeliveryStatus from, DeliveryStatus to) {
        Delivery delivery = deliveryIn(from);

        assertThatThrownBy(() -> delivery.changeStatus(to, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
    }

    @Test
    void sameStatusIsIgnored() {
        Delivery delivery = deliveryIn(DeliveryStatus.DELIVERED);

        delivery.changeStatus(DeliveryStatus.DELIVERED, NOW);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void courierCannotBeAssignedToFinishedDelivery() {
        Delivery delivery = deliveryIn(DeliveryStatus.CANCELLED);

        assertThatThrownBy(() -> delivery.assignCourier(5L, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Delivery deliveryIn(DeliveryStatus status) {
        Delivery delivery = Delivery.forOrder(100L, NOW);
        if (status != DeliveryStatus.ASSIGNED) {
            delivery.changeStatus(status, NOW);
        }
        return delivery;
    }
}
