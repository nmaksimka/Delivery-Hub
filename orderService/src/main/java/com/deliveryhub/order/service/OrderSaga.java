package com.deliveryhub.order.service;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.CourierAssignedEvent;
import com.deliveryhub.contracts.events.PaymentCapturedEvent;
import com.deliveryhub.contracts.events.PaymentHeldEvent;
import com.deliveryhub.contracts.events.PaymentRefundedEvent;
import com.deliveryhub.contracts.events.PaymentRejectedEvent;
import com.deliveryhub.contracts.events.PaymentReleasedEvent;
import com.deliveryhub.order.entity.CancellationReason;
import com.deliveryhub.order.entity.Order;
import com.deliveryhub.order.inbox.ProcessedEvents;
import com.deliveryhub.order.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderSaga {

    static final String RELEASED_FOR_DELIVERY = "DELIVERY_REJECTED";

    private static final Logger log = LoggerFactory.getLogger(OrderSaga.class);

    private final OrderRepository orderRepository;
    private final ProcessedEvents processedEvents;
    private final OrderCancellation cancellation;
    private final Clock clock;

    public OrderSaga(OrderRepository orderRepository, ProcessedEvents processedEvents,
                     OrderCancellation cancellation, Clock clock) {
        this.orderRepository = orderRepository;
        this.processedEvents = processedEvents;
        this.cancellation = cancellation;
        this.clock = clock;
    }

    @Transactional
    public void onPaymentHeld(PaymentHeldEvent event) {
        firstDelivery(event.getEventId(), Topics.PAYMENT_HELD, event.getOrderId()).ifPresent(order -> {
            if (order.isCancelled()) {
                cancellation.repeatCompensation(order);
            } else if (order.markPaymentHeld(clock.instant())) {
                log.info("Payment of order {} is held, waiting for a courier", order.getId());
            }
        });
    }

    @Transactional
    public void onPaymentRejected(PaymentRejectedEvent event) {
        firstDelivery(event.getEventId(), Topics.PAYMENT_REJECTED, event.getOrderId()).ifPresent(order -> {
            log.info("Payment of order {} is rejected: {}", order.getId(), event.getReason());
            cancelIfOpen(order, CancellationReason.PAYMENT_REJECTED);
        });
    }

    @Transactional
    public void onCourierAssigned(CourierAssignedEvent event) {
        firstDelivery(event.getEventId(), Topics.COURIER_ASSIGNED, event.getOrderId()).ifPresent(order -> {
            if (!order.acceptDeliveryEvent(event.getSequence())) {
                log.info("Courier assignment {} of order {} is older than the applied event, dropping",
                        event.getEventId(), order.getId());
                return;
            }
            if (order.markCourierAssigned(clock.instant())) {
                log.info("Courier {} assigned to order {}", event.getCourierId(), order.getId());
            }
        });
    }

    @Transactional
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        firstDelivery(event.getEventId(), Topics.PAYMENT_CAPTURED, event.getOrderId()).ifPresent(order -> {
            if (order.confirmPayment(clock.instant())) {
                log.info("Order {} is paid and confirmed", order.getId());
            } else {
                log.info("Order {} is {}, captured payment changes nothing", order.getId(), order.getStatus());
            }
        });
    }

    @Transactional
    public void onPaymentReleased(PaymentReleasedEvent event) {
        firstDelivery(event.getEventId(), Topics.PAYMENT_RELEASED, event.getOrderId()).ifPresent(order -> {
            CancellationReason reason = RELEASED_FOR_DELIVERY.equals(event.getReason())
                    ? CancellationReason.DELIVERY_REJECTED
                    : CancellationReason.UNKNOWN;
            cancelIfOpen(order, reason);
        });
    }

    @Transactional
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        firstDelivery(event.getEventId(), Topics.PAYMENT_REFUNDED, event.getOrderId()).ifPresent(order -> {
            if (order.recordRefund(event.getRefundedAt())) {
                log.info("Money of cancelled order {} is refunded", order.getId());
            }
        });
    }

    private void cancelIfOpen(Order order, CancellationReason reason) {
        if (order.getStatus().isFinal()) {
            log.info("Order {} is already {}, nothing to cancel", order.getId(), order.getStatus());
            return;
        }
        cancellation.cancel(order, reason);
    }

    private Optional<Order> firstDelivery(UUID eventId, String topic, long orderId) {
        if (!processedEvents.registerFirstDelivery(eventId, topic)) {
            log.info("Event {} from {} is already processed, skipping", eventId, topic);
            return Optional.empty();
        }
        return Optional.of(orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order with ID: " + orderId + " was not found")));
    }
}
