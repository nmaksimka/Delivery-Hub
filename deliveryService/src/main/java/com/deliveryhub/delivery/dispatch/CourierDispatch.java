package com.deliveryhub.delivery.dispatch;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.CourierAssignedEvent;
import com.deliveryhub.contracts.events.DeliveryRejectedEvent;
import com.deliveryhub.contracts.events.PaymentHeldEvent;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.inbox.ProcessedEvents;
import com.deliveryhub.delivery.outbox.Outbox;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class CourierDispatch {

    public static final String NO_FREE_COURIER = "NO_FREE_COURIER";

    private static final Logger log = LoggerFactory.getLogger(CourierDispatch.class);

    private final DeliveryRepository deliveryRepository;
    private final CourierPool courierPool;
    private final ProcessedEvents processedEvents;
    private final Outbox outbox;
    private final Clock clock;

    public CourierDispatch(DeliveryRepository deliveryRepository, CourierPool courierPool,
                           ProcessedEvents processedEvents, Outbox outbox, Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.courierPool = courierPool;
        this.processedEvents = processedEvents;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void onPaymentHeld(PaymentHeldEvent event) {
        if (!processedEvents.registerFirstDelivery(event.getEventId(), Topics.PAYMENT_HELD)) {
            log.info("Event {} is already processed, skipping", event.getEventId());
            return;
        }
        if (deliveryRepository.existsByOrderId(event.getOrderId())) {
            log.info("Order {} already has a delivery, courier is not assigned again", event.getOrderId());
            return;
        }
        Instant now = clock.instant();
        Optional<Long> courier = courierPool.freeCourier();
        if (courier.isEmpty()) {
            reject(event, now);
            return;
        }
        Delivery delivery = deliveryRepository.save(
                Delivery.forOrder(event.getOrderId(), event.getUserId(), courier.get(), null, now));
        UUID eventId = UUID.randomUUID();
        outbox.append("Delivery", delivery.getId(), Topics.COURIER_ASSIGNED, eventId, new CourierAssignedEvent(
                eventId, delivery.getId(), delivery.getOrderId(), courier.get(), now, delivery.nextEventSequence()));
        log.info("Courier {} assigned to order {}, delivery {}", courier.get(), event.getOrderId(), delivery.getId());
    }

    private void reject(PaymentHeldEvent event, Instant now) {
        UUID eventId = UUID.randomUUID();
        outbox.append("Order", event.getOrderId(), Topics.DELIVERY_REJECTED, eventId, new DeliveryRejectedEvent(
                eventId, event.getOrderId(), event.getUserId(), NO_FREE_COURIER, now));
        log.info("No free courier for order {}, delivery is rejected", event.getOrderId());
    }
}
