package com.deliveryhub.order.listener;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.CourierAssignedEvent;
import com.deliveryhub.contracts.events.PaymentCapturedEvent;
import com.deliveryhub.contracts.events.PaymentHeldEvent;
import com.deliveryhub.contracts.events.PaymentRefundedEvent;
import com.deliveryhub.contracts.events.PaymentRejectedEvent;
import com.deliveryhub.contracts.events.PaymentReleasedEvent;
import com.deliveryhub.order.service.OrderSaga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SagaEventListener {

    private static final Logger log = LoggerFactory.getLogger(SagaEventListener.class);
    private static final String GROUP = "order-service-group";

    private final OrderSaga saga;

    public SagaEventListener(OrderSaga saga) {
        this.saga = saga;
    }

    @KafkaListener(topics = Topics.PAYMENT_HELD, groupId = GROUP)
    public void onPaymentHeld(PaymentHeldEvent event) {
        log.info("Received payment-held: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        saga.onPaymentHeld(event);
    }

    @KafkaListener(topics = Topics.PAYMENT_REJECTED, groupId = GROUP)
    public void onPaymentRejected(PaymentRejectedEvent event) {
        log.info("Received payment-rejected: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        saga.onPaymentRejected(event);
    }

    @KafkaListener(topics = Topics.COURIER_ASSIGNED, groupId = GROUP)
    public void onCourierAssigned(CourierAssignedEvent event) {
        log.info("Received courier-assigned: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        saga.onCourierAssigned(event);
    }

    @KafkaListener(topics = Topics.PAYMENT_CAPTURED, groupId = GROUP)
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        log.info("Received payment-captured: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        saga.onPaymentCaptured(event);
    }

    @KafkaListener(topics = Topics.PAYMENT_RELEASED, groupId = GROUP)
    public void onPaymentReleased(PaymentReleasedEvent event) {
        log.info("Received payment-released: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        saga.onPaymentReleased(event);
    }

    @KafkaListener(topics = Topics.PAYMENT_REFUNDED, groupId = GROUP)
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        log.info("Received payment-refunded: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        saga.onPaymentRefunded(event);
    }
}
