package com.deliveryhub.order.service;

import com.deliveryhub.contracts.events.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String ORDER_CREATED_TOPIC = "order-created";

    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info("Publishing order created event for orderId: {}", event.getOrderId());
        kafkaTemplate.send(ORDER_CREATED_TOPIC, event.getOrderId().toString(), event)
                .whenComplete(((result, ex) -> {
                    if(ex != null) {
                        log.error("Failed to send order event", ex);
                    } else {
                        log.info("Order event sent successfully to topic {}", ORDER_CREATED_TOPIC);
                    }
                }));
    }
}
