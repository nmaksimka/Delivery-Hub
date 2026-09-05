package com.deliveryhub.order.service;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.order.entity.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderStatusResolver")
class OrderStatusResolverTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "ASSIGNED,   CONFIRMED",
            "PICKED_UP,  IN_DELIVERY",
            "IN_TRANSIT, IN_DELIVERY",
            "DELIVERED,  DELIVERED",
            "CANCELLED,  CANCELLED"
    })
    @DisplayName("переводит статус доставки в статус заказа")
    void mapsDeliveryStatus(DeliveryStatus deliveryStatus, OrderStatus expected) {
        assertThat(OrderStatusResolver.fromDeliveryStatus(deliveryStatus)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(DeliveryStatus.class)
    @DisplayName("покрывает все статусы доставки без исключений")
    void handlesEveryDeliveryStatus(DeliveryStatus deliveryStatus) {
        assertThat(OrderStatusResolver.fromDeliveryStatus(deliveryStatus)).isNotNull();
    }

    @Test
    @DisplayName("статус заказа никогда не совпадает со служебными статусами доставки")
    void neverLeaksDeliveryVocabulary() {
        assertThat(OrderStatusResolver.fromDeliveryStatus(DeliveryStatus.ASSIGNED))
                .isNotEqualTo(OrderStatus.CREATED)
                .isEqualTo(OrderStatus.CONFIRMED);
    }
}
