package com.deliveryhub.order.service;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
import com.deliveryhub.order.entity.CancellationReason;
import com.deliveryhub.order.entity.Order;
import com.deliveryhub.order.outbox.Outbox;
import com.deliveryhub.order.promo.PromoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Component
public class OrderCancellation {

    private static final Logger log = LoggerFactory.getLogger(OrderCancellation.class);

    private final Outbox outbox;
    private final PromoService promoService;
    private final Clock clock;

    public OrderCancellation(Outbox outbox, PromoService promoService, Clock clock) {
        this.outbox = outbox;
        this.promoService = promoService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean cancel(Order order, CancellationReason reason) {
        boolean cancelled = reason == CancellationReason.CUSTOMER
                ? order.cancelByCustomer(clock.instant())
                : order.cancel(reason, clock.instant());
        if (!cancelled) {
            return false;
        }
        log.info("Order {} is cancelled, reason {}", order.getId(), reason);
        if (order.hasPromo()) {
            promoService.release(order.getId());
        }
        announce(order);
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void repeatCompensation(Order order) {
        log.info("Order {} is already cancelled, asking participants to compensate again", order.getId());
        announce(order);
    }

    private void announce(Order order) {
        OrderCancelledEvent event = OrderCancelledEvent.newBuilder()
                .setEventId(UUID.randomUUID())
                .setOrderId(order.getId())
                .setUserId(order.getUserId())
                .setCancelledAt(order.getUpdatedAt())
                .setSequence(order.nextEventSequence())
                .build();
        outbox.append("Order", order.getId(), Topics.ORDER_CANCELLED, event.getEventId(), event);
    }
}
