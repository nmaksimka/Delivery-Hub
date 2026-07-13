package com.deliveryhub.delivery.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.deliveryhub.delivery.service.DeliveryService;
import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
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

    @KafkaListener(topics = Topics.ORDER_CREATED, groupId = "delivery-service-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Received order-created event: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        deliveryService.createDeliveryFromOrderEvent(event);
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED, groupId = "delivery-service-group")
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("Received order-cancelled event: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        deliveryService.cancelDeliveryOfOrder(event);
    }
}
