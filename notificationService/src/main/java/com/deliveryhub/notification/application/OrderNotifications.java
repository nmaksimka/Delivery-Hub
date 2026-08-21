package com.deliveryhub.notification.application;

import com.deliveryhub.notification.domain.Notification;
import com.deliveryhub.notification.domain.NotificationRepository;
import com.deliveryhub.notification.domain.OrderRecipient;
import com.deliveryhub.notification.domain.OrderRecipientRepository;
import com.deliveryhub.notification.infrastructure.inbox.ProcessedEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongFunction;

@Service
public class OrderNotifications {

    private static final Logger log = LoggerFactory.getLogger(OrderNotifications.class);

    private final NotificationRepository notifications;
    private final OrderRecipientRepository recipients;
    private final ProcessedEvents processedEvents;
    private final Clock clock;

    public OrderNotifications(NotificationRepository notifications, OrderRecipientRepository recipients,
                              ProcessedEvents processedEvents, Clock clock) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.processedEvents = processedEvents;
        this.clock = clock;
    }

    @Transactional
    public void orderCreated(UUID eventId, long orderId, long userId, BigDecimal amount) {
        if (alreadyHandled(eventId, "order-created")) {
            return;
        }
        Instant now = clock.instant();
        if (!recipients.existsById(orderId)) {
            recipients.save(new OrderRecipient(orderId, userId, now));
        }
        notifications.save(Notification.orderCreated(eventId, userId, orderId, amount, now));
    }

    @Transactional
    public void paymentRejected(UUID eventId, long orderId, long userId, BigDecimal amount, String reason) {
        if (alreadyHandled(eventId, "payment-rejected")) {
            return;
        }
        notifications.save(Notification.paymentRejected(eventId, userId, orderId, amount, reason, clock.instant()));
    }

    @Transactional
    public void deliveryRejected(UUID eventId, long orderId, long userId, String reason) {
        if (alreadyHandled(eventId, "delivery-rejected")) {
            return;
        }
        notifications.save(Notification.deliveryRejected(eventId, userId, orderId, reason, clock.instant()));
    }

    @Transactional
    public void orderCancelled(UUID eventId, long orderId, long userId) {
        if (alreadyHandled(eventId, "order-cancelled")) {
            return;
        }
        notifications.save(Notification.orderCancelled(eventId, userId, orderId, clock.instant()));
    }

    @Transactional
    public void courierAssigned(UUID eventId, long orderId) {
        notifyRecipientOf(eventId, "courier-assigned", orderId,
                userId -> Notification.courierAssigned(eventId, userId, orderId, clock.instant()));
    }

    @Transactional
    public void orderDelivered(UUID eventId, long orderId) {
        notifyRecipientOf(eventId, "order-delivered", orderId,
                userId -> Notification.orderDelivered(eventId, userId, orderId, clock.instant()));
    }

    private void notifyRecipientOf(UUID eventId, String eventType, long orderId,
                                   LongFunction<Notification> notification) {
        if (alreadyHandled(eventId, eventType)) {
            return;
        }
        Optional<OrderRecipient> recipient = recipients.findById(orderId);
        if (recipient.isEmpty()) {
            log.warn("Skipping {} for unknown order id={}", eventType, orderId);
            return;
        }
        notifications.save(notification.apply(recipient.get().getUserId()));
    }

    private boolean alreadyHandled(UUID eventId, String eventType) {
        boolean first = processedEvents.registerFirstDelivery(eventId, eventType);
        if (!first) {
            log.info("Duplicate {} event id={} ignored", eventType, eventId);
        }
        return !first;
    }
}
