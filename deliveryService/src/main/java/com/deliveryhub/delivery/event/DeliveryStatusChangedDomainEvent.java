package com.deliveryhub.delivery.event;

import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;

public record DeliveryStatusChangedDomainEvent(DeliveryStatusUpdatedEvent payload) {
}
