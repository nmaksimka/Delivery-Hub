package com.deliveryhub.delivery.entity;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryTest {

    @Test
    void newDeliveryIsAssigned() {
        assertThat(Delivery.forOrder(100L).getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
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

        delivery.changeStatus(to);

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

        assertThatThrownBy(() -> delivery.changeStatus(to))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
    }

    @Test
    void sameStatusIsIgnored() {
        Delivery delivery = deliveryIn(DeliveryStatus.DELIVERED);

        delivery.changeStatus(DeliveryStatus.DELIVERED);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void courierCannotBeAssignedToFinishedDelivery() {
        Delivery delivery = deliveryIn(DeliveryStatus.CANCELLED);

        assertThatThrownBy(() -> delivery.assignCourier(5L))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Delivery deliveryIn(DeliveryStatus status) {
        Delivery delivery = Delivery.forOrder(100L);
        if (status != DeliveryStatus.ASSIGNED) {
            delivery.changeStatus(status);
        }
        return delivery;
    }
}
