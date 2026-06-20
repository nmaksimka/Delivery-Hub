package com.deliveryhub.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.order.event.OrderCreatedDomainEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    public static final String ORDER_CREATED_TOPIC = "order-created";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * AFTER_COMMIT закрывает dual-write: раньше событие улетало до коммита,
     * и при откате транзакции доставка создавалась под несуществующий заказ.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedDomainEvent domainEvent) {
        publishOrderCreated(domainEvent.payload());
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info("Publishing order-created event for orderId={}", event.orderId());
        kafkaTemplate.send(ORDER_CREATED_TOPIC, event.orderId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish order-created event for orderId={}", event.orderId(), ex);
                    } else {
                        log.debug("order-created event published for orderId={}", event.orderId());
                    }
                });
    }
}
