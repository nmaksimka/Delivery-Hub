package com.deliveryhub.delivery.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.delivery.event.DeliveryStatusChangedDomainEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DeliveryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DeliveryEventPublisher.class);

    public static final String DELIVERY_STATUS_TOPIC = "delivery-status-updated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DeliveryEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeliveryStatusChanged(DeliveryStatusChangedDomainEvent domainEvent) {
        publishStatusUpdate(domainEvent.payload());
    }

    public void publishStatusUpdate(DeliveryStatusUpdatedEvent event) {
        log.info("Publishing delivery status update: orderId={}, status={}", event.getOrderId(), event.getStatus());
        kafkaTemplate.send(DELIVERY_STATUS_TOPIC, event.getOrderId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish delivery status for orderId={}", event.getOrderId(), ex);
                    } else {
                        log.debug("Delivery status published for orderId={}", event.getOrderId());
                    }
                });
    }
}
