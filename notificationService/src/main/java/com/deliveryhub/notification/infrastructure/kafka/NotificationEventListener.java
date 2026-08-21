package com.deliveryhub.notification.infrastructure.kafka;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.CourierAssignedEvent;
import com.deliveryhub.contracts.events.DeliveryRejectedEvent;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.contracts.events.PaymentRejectedEvent;
import com.deliveryhub.notification.application.OrderNotifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationEventListener {

    static final List<String> TOPICS = List.of(Topics.ORDER_CREATED, Topics.PAYMENT_REJECTED,
            Topics.DELIVERY_REJECTED, Topics.COURIER_ASSIGNED, Topics.DELIVERY_STATUS_UPDATED,
            Topics.ORDER_CANCELLED);

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
    private static final String GROUP = "notification-service-group";

    private final OrderNotifications notifications;

    public NotificationEventListener(OrderNotifications notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED, groupId = GROUP)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.debug("Received order-created: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        notifications.orderCreated(event.getEventId(), event.getOrderId(), event.getUserId(), event.getTotalAmount());
    }

    @KafkaListener(topics = Topics.PAYMENT_REJECTED, groupId = GROUP)
    public void onPaymentRejected(PaymentRejectedEvent event) {
        log.debug("Received payment-rejected: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        notifications.paymentRejected(event.getEventId(), event.getOrderId(), event.getUserId(), event.getAmount(),
                event.getReason());
    }

    @KafkaListener(topics = Topics.DELIVERY_REJECTED, groupId = GROUP)
    public void onDeliveryRejected(DeliveryRejectedEvent event) {
        log.debug("Received delivery-rejected: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        notifications.deliveryRejected(event.getEventId(), event.getOrderId(), event.getUserId(), event.getReason());
    }

    @KafkaListener(topics = Topics.COURIER_ASSIGNED, groupId = GROUP)
    public void onCourierAssigned(CourierAssignedEvent event) {
        log.debug("Received courier-assigned: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        notifications.courierAssigned(event.getEventId(), event.getOrderId());
    }

    @KafkaListener(topics = Topics.DELIVERY_STATUS_UPDATED, groupId = GROUP)
    public void onDeliveryStatusUpdated(DeliveryStatusUpdatedEvent event) {
        if (event.getStatus() == DeliveryStatus.DELIVERED) {
            notifications.orderDelivered(event.getEventId(), event.getOrderId());
        }
    }

    @KafkaListener(topics = Topics.ORDER_CANCELLED, groupId = GROUP)
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.debug("Received order-cancelled: eventId={}, orderId={}", event.getEventId(), event.getOrderId());
        notifications.orderCancelled(event.getEventId(), event.getOrderId(), event.getUserId());
    }
}
