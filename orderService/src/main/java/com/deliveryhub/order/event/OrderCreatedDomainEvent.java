package com.deliveryhub.order.event;

import com.deliveryhub.contracts.events.OrderCreatedEvent;

public record OrderCreatedDomainEvent(OrderCreatedEvent payload) {
}
