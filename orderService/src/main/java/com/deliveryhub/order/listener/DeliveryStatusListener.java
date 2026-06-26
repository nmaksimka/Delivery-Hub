package com.deliveryhub.order.listener;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DeliveryStatusListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryStatusListener.class);

    private final OrderService orderService;

    public DeliveryStatusListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = Topics.DELIVERY_STATUS_UPDATED, groupId = "order-service-group")
    public void handleDeliveryStatusUpdated(DeliveryStatusUpdatedEvent event) {
        log.info("Received delivery status update: eventId={}, orderId={}, status={}",
                event.eventId(), event.orderId(), event.status());
        orderService.applyDeliveryStatus(event);
    }
}
