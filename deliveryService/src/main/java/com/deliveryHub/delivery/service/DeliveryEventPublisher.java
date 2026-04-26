package com.deliveryHub.delivery.service;

import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String DELIVERY_STATUS_TOPIC = "delivery-status-updated";

    public void publishStatusUpdate(DeliveryStatusUpdatedEvent updatedEvent) {
        log.info("Publishing delivery status update for orderId: {}", updatedEvent.getOrderId());
        kafkaTemplate.send(DELIVERY_STATUS_TOPIC, updatedEvent.getOrderId().toString(), updatedEvent)
                .whenComplete((result, ex) -> {
                    if(ex != null) log.error("failed to send status event", ex);
                    else log.info("status info sent successfully");
                });
    }
}
