package com.deliveryHub.delivery.listener;

import com.deliveryHub.delivery.service.DeliveryService;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {
    private final DeliveryService deliveryService;

    @KafkaListener(topics = "order-created", groupId = "delivery-service-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent for orderId: {}", event.getOrderId());
        deliveryService.createDeliveryFromOrderEvent(event);
    }
}
