package com.deliveryhub.delivery.dispatch;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.CourierAssignedEvent;
import com.deliveryhub.contracts.events.DeliveryRejectedEvent;
import com.deliveryhub.contracts.events.GeoPoint;
import com.deliveryhub.contracts.events.PaymentHeldEvent;
import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.entity.GeoPosition;
import com.deliveryhub.delivery.inbox.ProcessedEvents;
import com.deliveryhub.delivery.outbox.Outbox;
import com.deliveryhub.delivery.repository.CancelledOrders;
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
    private final CancelledOrders cancelledOrders;
    private final CourierPool courierPool;
    private final ProcessedEvents processedEvents;
    private final Outbox outbox;
    private final Clock clock;

    public CourierDispatch(DeliveryRepository deliveryRepository, CancelledOrders cancelledOrders,
                           CourierPool courierPool, ProcessedEvents processedEvents, Outbox outbox, Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.cancelledOrders = cancelledOrders;
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
        if (cancelledOrders.contains(event.getOrderId())) {
            log.info("Order {} is already cancelled, courier is not assigned", event.getOrderId());
            return;
        }
        Instant now = clock.instant();
        Optional<Courier> courier = courierPool.occupyNearest(event, now);
        if (courier.isEmpty()) {
            reject(event, now);
            return;
        }
        Long courierId = courier.get().getId();
        Delivery delivery = Delivery.forOrder(event.getOrderId(), event.getUserId(), courierId, null, now);
        delivery.routeBetween(position(event.getRestaurantLocation()), position(event.getDeliveryLocation()));
        delivery.deliverTo(event.getDeliveryAddress(), event.getDeliveryComment());
        delivery = deliveryRepository.save(delivery);
        UUID eventId = UUID.randomUUID();
        outbox.append("Delivery", delivery.getId(), Topics.COURIER_ASSIGNED, eventId, new CourierAssignedEvent(
                eventId, delivery.getId(), delivery.getOrderId(), courierId, now, delivery.nextEventSequence()));
        log.info("Courier {} assigned to order {}, delivery {}", courierId, event.getOrderId(), delivery.getId());
    }

    private static GeoPosition position(GeoPoint point) {
        return point == null ? null : new GeoPosition(point.getLatitude(), point.getLongitude());
    }

    private void reject(PaymentHeldEvent event, Instant now) {
        UUID eventId = UUID.randomUUID();
        outbox.append("Order", event.getOrderId(), Topics.DELIVERY_REJECTED, eventId, new DeliveryRejectedEvent(
                eventId, event.getOrderId(), event.getUserId(), NO_FREE_COURIER, now));
        log.info("No free courier for order {}, delivery is rejected", event.getOrderId());
    }
}
