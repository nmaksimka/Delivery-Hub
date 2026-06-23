package com.deliveryhub.delivery.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.deliveryhub.delivery.service.DeliveryService;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final DeliveryService deliveryService;

    public OrderEventListener(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @KafkaListener(topics = "order-created", groupId = "delivery-service-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent for orderId: {}", event.orderId());
        deliveryService.createDeliveryFromOrderEvent(event);
    }
}
