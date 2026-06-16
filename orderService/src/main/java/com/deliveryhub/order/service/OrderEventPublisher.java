package com.deliveryhub.order.service;

import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.order.event.OrderCreatedDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {
    public static final String ORDER_CREATED_TOPIC = "order-created";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * AFTER_COMMIT закрывает dual-write: раньше событие улетало до коммита,
     * и при откате транзакции доставка создавалась под несуществующий заказ.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedDomainEvent domainEvent) {
        publishOrderCreated(domainEvent.payload());
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info("Publishing order-created event for orderId={}", event.getOrderId());
        kafkaTemplate.send(ORDER_CREATED_TOPIC, event.getOrderId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish order-created event for orderId={}", event.getOrderId(), ex);
                    } else {
                        log.debug("order-created event published for orderId={}", event.getOrderId());
                    }
                });
    }
}
