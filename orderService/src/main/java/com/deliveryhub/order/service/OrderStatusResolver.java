package com.deliveryhub.order.service;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.order.entity.OrderStatus;

/**
 * Статус доставки и статус заказа — разные домены.
 * Раньше orderService писал статус доставки прямо в заказ.
 */
public final class OrderStatusResolver {

    private OrderStatusResolver() {
    }

    public static OrderStatus fromDeliveryStatus(DeliveryStatus deliveryStatus) {
        return switch (deliveryStatus) {
            case ASSIGNED -> OrderStatus.CONFIRMED;
            case PICKED_UP, IN_TRANSIT -> OrderStatus.IN_DELIVERY;
            case DELIVERED -> OrderStatus.DELIVERED;
            case CANCELLED -> OrderStatus.CANCELLED;
        };
    }
}
