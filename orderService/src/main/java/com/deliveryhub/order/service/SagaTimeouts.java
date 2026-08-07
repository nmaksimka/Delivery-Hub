package com.deliveryhub.order.service;

import com.deliveryhub.order.entity.CancellationReason;
import com.deliveryhub.order.entity.OrderStatus;
import com.deliveryhub.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Limit;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@EnableConfigurationProperties(SagaProperties.class)
public class SagaTimeouts {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeouts.class);

    private final OrderRepository orderRepository;
    private final OrderCancellation cancellation;
    private final SagaProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public SagaTimeouts(OrderRepository orderRepository, OrderCancellation cancellation, SagaProperties properties,
                        PlatformTransactionManager transactionManager, Clock clock) {
        this.orderRepository = orderRepository;
        this.cancellation = cancellation;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.saga.check-interval:15s}", initialDelayString = "${app.saga.check-interval:15s}")
    public void cancelStuckOrders() {
        Instant now = clock.instant();
        int cancelled = cancelStuck(OrderStatus.CREATED, properties.paymentTimeout(), CancellationReason.PAYMENT_TIMEOUT, now)
                + cancelStuck(OrderStatus.PAYMENT_HELD, properties.courierTimeout(), CancellationReason.COURIER_TIMEOUT, now)
                + cancelStuck(OrderStatus.COURIER_ASSIGNED, properties.confirmationTimeout(),
                CancellationReason.CONFIRMATION_TIMEOUT, now);
        if (cancelled > 0) {
            log.info("Cancelled {} orders stuck in the saga", cancelled);
        }
    }

    private int cancelStuck(OrderStatus status, Duration timeout, CancellationReason reason, Instant now) {
        Instant cutoff = now.minus(timeout);
        int cancelled = 0;
        for (Long orderId : orderRepository.findIdsStuckIn(status, cutoff, Limit.of(properties.batchSize()))) {
            if (cancelIfStillStuck(orderId, status, cutoff, reason)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    private boolean cancelIfStillStuck(Long orderId, OrderStatus status, Instant cutoff, CancellationReason reason) {
        try {
            return Boolean.TRUE.equals(transaction.execute(tx -> orderRepository.findById(orderId)
                    .filter(order -> order.isStuckIn(status, cutoff))
                    .map(order -> cancellation.cancel(order, reason))
                    .orElse(false)));
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("Order {} changed while timing out, the saga answer wins", orderId);
            return false;
        }
    }
}
