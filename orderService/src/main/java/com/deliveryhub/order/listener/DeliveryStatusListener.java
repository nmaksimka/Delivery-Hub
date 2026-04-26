package com.deliveryhub.order.listener;

import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryStatusListener {
    private final OrderService orderService;

    @KafkaListener(topics = "delivery-status-updated", groupId = "order-service-group")
    public void handleDeliveryStatusUpdated(DeliveryStatusUpdatedEvent updatedEvent) {
        log.info("received status update for orderId: {}, new status: {}", updatedEvent.getOrderId(), updatedEvent.getStatus());
        orderService.updateOrderStatus(updatedEvent.getOrderId(), updatedEvent.getStatus());
    }
}
