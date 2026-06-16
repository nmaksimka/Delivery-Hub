package com.deliveryhub.order.event;

import com.deliveryhub.contracts.events.OrderCreatedEvent;

/**
 * Внутреннее Spring-событие. Публикуется внутри транзакции,
 * а в Kafka уходит только после успешного коммита — см. OrderEventPublisher.
 */
public record OrderCreatedDomainEvent(OrderCreatedEvent payload) {
}
