package com.deliveryhub.payment.infrastructure.kafka;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.CourierAssignedEvent;
import com.deliveryhub.contracts.events.DeliveryRejectedEvent;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.payment.application.PaymentSaga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SagaEventListener {

    private static final Logger log = LoggerFactory.getLogger(SagaEventListener.class);
    private static final String GROUP = "payment-service-group";

    private final PaymentSaga saga;

    public SagaEventListener(PaymentSaga saga) {
        this.saga = saga;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED, groupId = GROUP)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received order-created: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        saga.onOrderCreated(event);
    }

    @KafkaListener(topics = Topics.COURIER_ASSIGNED, groupId = GROUP)
    public void onCourierAssigned(CourierAssignedEvent event) {
        log.info("Received courier-assigned: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        saga.onCourierAssigned(event);
    }

    @KafkaListener(topics = Topics.DELIVERY_REJECTED, groupId = GROUP)
    public void onDeliveryRejected(DeliveryRejectedEvent event) {
        log.info("Received delivery-rejected: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        saga.onDeliveryRejected(event);
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED, groupId = GROUP)
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Received order-cancelled: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        saga.onOrderCancelled(event);
    }
}
