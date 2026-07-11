package com.deliveryhub.delivery.entity;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.delivery.exception.DeliveryFinishedException;
import com.deliveryhub.delivery.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryTest {

    private static final Instant NOW = Instant.parse("2026-07-09T10:15:30Z");

    @Test
    void newDeliveryIsAssigned() {
        assertThat(Delivery.forOrder(100L, 42L, NOW).getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
    }

    @ParameterizedTest
    @CsvSource({
            "ASSIGNED,   PICKED_UP",
            "ASSIGNED,   CANCELLED",
            "PICKED_UP,  IN_TRANSIT",
            "IN_TRANSIT, DELIVERED",
            "IN_TRANSIT, CANCELLED"
    })
    void movesOneStepForwardOrCancels(DeliveryStatus from, DeliveryStatus to) {
        Delivery delivery = deliveryIn(from);

        assertThat(delivery.changeStatus(to, NOW)).isTrue();

        assertThat(delivery.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
            "ASSIGNED,   IN_TRANSIT",
            "ASSIGNED,   DELIVERED",
            "PICKED_UP,  DELIVERED",
            "PICKED_UP,  ASSIGNED",
            "IN_TRANSIT, PICKED_UP",
            "DELIVERED,  IN_TRANSIT",
            "DELIVERED,  CANCELLED",
            "CANCELLED,  ASSIGNED"
    })
    void rejectsJumpsBackwardAndPostFinalTransitions(DeliveryStatus from, DeliveryStatus to) {
        Delivery delivery = deliveryIn(from);

        assertThatThrownBy(() -> delivery.changeStatus(to, NOW))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
    }

    @Test
    void sameStatusIsNotAChange() {
        Delivery delivery = deliveryIn(DeliveryStatus.DELIVERED);

        assertThat(delivery.changeStatus(DeliveryStatus.DELIVERED, NOW)).isFalse();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void courierCannotBeAssignedToFinishedDelivery() {
        Delivery delivery = deliveryIn(DeliveryStatus.CANCELLED);

        assertThatThrownBy(() -> delivery.assignCourier(5L, NOW))
                .isInstanceOf(DeliveryFinishedException.class);
    }

    private static Delivery deliveryIn(DeliveryStatus status) {
        Delivery delivery = Delivery.forOrder(100L, 42L, NOW);
        ReflectionTestUtils.setField(delivery, "status", status);
        return delivery;
    }
}
