package com.deliveryhub.delivery.event;

import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;

/** Внутреннее событие; в Kafka уходит только после коммита транзакции. */
public record DeliveryStatusChangedDomainEvent(DeliveryStatusUpdatedEvent payload) {
}
