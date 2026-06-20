package com.deliveryhub.order.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.order.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DeliveryStatusListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryStatusListener.class);

    private final OrderService orderService;

    public DeliveryStatusListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "delivery-status-updated", groupId = "order-service-group")
    public void handleDeliveryStatusUpdated(DeliveryStatusUpdatedEvent event) {
        log.info("Received delivery status update: orderId={}, status={}", event.orderId(), event.status());
        orderService.applyDeliveryStatus(event.orderId(), event.status());
    }
}
