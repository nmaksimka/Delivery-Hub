package com.deliveryhub.delivery.listener;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
import com.deliveryhub.contracts.events.PaymentCapturedEvent;
import com.deliveryhub.contracts.events.PaymentHeldEvent;
import com.deliveryhub.delivery.dispatch.CourierDispatch;
import com.deliveryhub.delivery.service.DeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final DeliveryService deliveryService;
    private final CourierDispatch courierDispatch;

    public OrderEventListener(DeliveryService deliveryService, CourierDispatch courierDispatch) {
        this.deliveryService = deliveryService;
        this.courierDispatch = courierDispatch;
    }

    @KafkaListener(topics = Topics.PAYMENT_HELD, groupId = "delivery-service-group")
    public void handlePaymentHeld(PaymentHeldEvent event) {
        log.info("Received payment-held event: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        courierDispatch.onPaymentHeld(event);
    }

    @KafkaListener(topics = Topics.PAYMENT_CAPTURED, groupId = "delivery-service-group")
    public void handlePaymentCaptured(PaymentCapturedEvent event) {
        log.info("Received payment-captured event: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        deliveryService.markOrderConfirmed(event);
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED, groupId = "delivery-service-group")
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("Received order-cancelled event: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        deliveryService.cancelDeliveryOfOrder(event);
    }
}
