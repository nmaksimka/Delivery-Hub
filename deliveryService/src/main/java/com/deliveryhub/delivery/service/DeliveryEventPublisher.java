package com.deliveryhub.delivery.service;

import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.delivery.event.DeliveryStatusChangedDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventPublisher {
    public static final String DELIVERY_STATUS_TOPIC = "delivery-status-updated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

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
