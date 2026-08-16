package com.deliveryhub.payment.application;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.CourierAssignedEvent;
import com.deliveryhub.contracts.events.DeliveryRejectedEvent;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.contracts.events.PaymentCapturedEvent;
import com.deliveryhub.contracts.events.PaymentHeldEvent;
import com.deliveryhub.contracts.events.PaymentRefundedEvent;
import com.deliveryhub.contracts.events.PaymentRejectedEvent;
import com.deliveryhub.contracts.events.PaymentReleasedEvent;
import com.deliveryhub.payment.domain.Payment;
import com.deliveryhub.payment.domain.PaymentException;
import com.deliveryhub.payment.domain.PaymentRepository;
import com.deliveryhub.payment.domain.PaymentStatus;
import com.deliveryhub.payment.infrastructure.inbox.ProcessedEvents;
import com.deliveryhub.payment.infrastructure.outbox.Outbox;
import org.apache.avro.specific.SpecificRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentSaga {

    public static final String RELEASED_FOR_DELIVERY = "DELIVERY_REJECTED";
    public static final String RELEASED_FOR_CANCELLATION = "ORDER_CANCELLED";

    private static final Logger log = LoggerFactory.getLogger(PaymentSaga.class);
    private static final String AGGREGATE_TYPE = "Payment";

    private final PaymentService paymentService;
    private final PaymentRepository payments;
    private final ProcessedEvents processedEvents;
    private final Outbox outbox;
    private final Clock clock;

    public PaymentSaga(PaymentService paymentService, PaymentRepository payments, ProcessedEvents processedEvents,
                       Outbox outbox, Clock clock) {
        this.paymentService = paymentService;
        this.payments = payments;
        this.processedEvents = processedEvents;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void onOrderCreated(OrderCreatedEvent event) {
        if (alreadyProcessed(event.getEventId(), Topics.ORDER_CREATED)) {
            return;
        }
        long orderId = event.getOrderId();
        try {
            announce(paymentService.open(orderId, event.getUserId(), event.getTotalAmount()), event);
        } catch (PaymentException ex) {
            log.info("Order {} cannot be paid: {}", orderId, ex.getMessage());
            publishRejected(orderId, event.getUserId(), event.getTotalAmount(), ex.getCode().name());
        }
    }

    private void announce(Payment payment, OrderCreatedEvent order) {
        long orderId = payment.getOrderId();
        switch (payment.getStatus()) {
            case HELD -> {
                UUID eventId = UUID.randomUUID();
                publish(orderId, Topics.PAYMENT_HELD, eventId, new PaymentHeldEvent(eventId, orderId,
                        payment.getUserId(), payment.getAmount(), payment.getProvider(), clock.instant(),
                        order.getRestaurantId(), order.getRestaurantLocation(), order.getDeliveryLocation()));
            }
            case REJECTED -> publishRejected(orderId, payment.getUserId(), payment.getAmount(),
                    payment.getDeclineReason().name());
            default -> log.info("Payment of order {} is already {}, saga has moved on", orderId,
                    payment.getStatus());
        }
    }

    @Transactional
    public void onCourierAssigned(CourierAssignedEvent event) {
        if (alreadyProcessed(event.getEventId(), Topics.COURIER_ASSIGNED)) {
            return;
        }
        Optional<Payment> held = lockedIn(event.getOrderId(), PaymentStatus.HELD);
        if (held.isEmpty()) {
            return;
        }
        Payment payment = paymentService.capture(event.getOrderId());
        UUID eventId = UUID.randomUUID();
        publish(payment.getOrderId(), Topics.PAYMENT_CAPTURED, eventId, new PaymentCapturedEvent(eventId,
                payment.getOrderId(), payment.getUserId(), payment.getAmount(), clock.instant()));
    }

    @Transactional
    public void onDeliveryRejected(DeliveryRejectedEvent event) {
        if (alreadyProcessed(event.getEventId(), Topics.DELIVERY_REJECTED)) {
            return;
        }
        if (lockedIn(event.getOrderId(), PaymentStatus.HELD).isPresent()) {
            release(event.getOrderId(), RELEASED_FOR_DELIVERY);
        }
    }

    @Transactional
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (alreadyProcessed(event.getEventId(), Topics.ORDER_CANCELLED)) {
            return;
        }
        Optional<Payment> payment = payments.findForUpdateByOrderId(event.getOrderId());
        if (payment.isEmpty()) {
            log.info("Order {} is cancelled before its payment, nothing to compensate", event.getOrderId());
            return;
        }
        switch (payment.get().getStatus()) {
            case HELD -> release(event.getOrderId(), RELEASED_FOR_CANCELLATION);
            case CAPTURED -> refund(event.getOrderId());
            default -> log.info("Payment of cancelled order {} is already {}", event.getOrderId(),
                    payment.get().getStatus());
        }
    }

    private void release(long orderId, String reason) {
        Payment payment = paymentService.release(orderId);
        UUID eventId = UUID.randomUUID();
        publish(orderId, Topics.PAYMENT_RELEASED, eventId, new PaymentReleasedEvent(eventId, orderId,
                payment.getUserId(), payment.getAmount(), clock.instant(), reason));
    }

    private void refund(long orderId) {
        Payment payment = paymentService.refund(orderId);
        UUID eventId = UUID.randomUUID();
        publish(orderId, Topics.PAYMENT_REFUNDED, eventId, new PaymentRefundedEvent(eventId, orderId,
                payment.getUserId(), payment.getAmount(), clock.instant()));
    }

    private void publishRejected(long orderId, long userId, BigDecimal amount, String reason) {
        UUID eventId = UUID.randomUUID();
        publish(orderId, Topics.PAYMENT_REJECTED, eventId,
                new PaymentRejectedEvent(eventId, orderId, userId, amount, reason, clock.instant()));
    }

    private Optional<Payment> lockedIn(long orderId, PaymentStatus expected) {
        Optional<Payment> payment = payments.findForUpdateByOrderId(orderId)
                .filter(found -> found.getStatus() == expected);
        if (payment.isEmpty()) {
            log.info("Payment of order {} is not {}, event changes nothing", orderId, expected);
        }
        return payment;
    }

    private boolean alreadyProcessed(UUID eventId, String topic) {
        if (processedEvents.registerFirstDelivery(eventId, topic)) {
            return false;
        }
        log.info("Event {} from {} is already processed, skipping", eventId, topic);
        return true;
    }

    private void publish(long orderId, String topic, UUID eventId, SpecificRecord event) {
        outbox.append(AGGREGATE_TYPE, orderId, topic, eventId, event);
    }
}
