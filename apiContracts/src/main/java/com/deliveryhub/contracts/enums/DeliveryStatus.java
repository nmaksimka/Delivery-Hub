package com.deliveryhub.contracts.enums;

/**
 * Статус доставки. Часть контракта между deliveryService и orderService,
 * поэтому живёт в общем модуле, а не внутри сервиса.
 */
public enum DeliveryStatus {
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED
}
