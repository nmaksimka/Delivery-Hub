package com.deliveryhub.delivery.service;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
import com.deliveryhub.contracts.events.PaymentCapturedEvent;
import com.deliveryhub.delivery.dto.CreateDeliveryRequest;
import com.deliveryhub.delivery.dto.DeliveryDto;
import com.deliveryhub.delivery.dto.UpdateDeliveryStatusRequest;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.exception.DeliveryAlreadyExistsException;
import com.deliveryhub.delivery.exception.ForbiddenException;
import com.deliveryhub.delivery.exception.OrderAlreadyCancelledException;
import com.deliveryhub.delivery.inbox.ProcessedEvents;
import com.deliveryhub.delivery.mapper.DeliveryMapper;
import com.deliveryhub.delivery.outbox.Outbox;
import com.deliveryhub.delivery.repository.CancelledOrders;
import com.deliveryhub.delivery.repository.CourierRepository;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import com.deliveryhub.delivery.security.CurrentUser;
import com.deliveryhub.delivery.tracking.TrackingUpdate;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final String AGGREGATE_TYPE = "Delivery";
    public static final String STATUS_CHANGED = "deliveries.status.changed";
    public static final String STATUS_TAG = "status";

    private final DeliveryRepository deliveryRepository;
    private final DeliveryMapper deliveryMapper;
    private final Outbox outbox;
    private final ProcessedEvents processedEvents;
    private final CancelledOrders cancelledOrders;
    private final CourierRepository courierRepository;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final ApplicationEventPublisher events;

    public DeliveryService(DeliveryRepository deliveryRepository,
                           DeliveryMapper deliveryMapper,
                           Outbox outbox,
                           ProcessedEvents processedEvents,
                           CancelledOrders cancelledOrders,
                           CourierRepository courierRepository,
                           Clock clock,
                           MeterRegistry meterRegistry,
                           ApplicationEventPublisher events) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryMapper = deliveryMapper;
        this.outbox = outbox;
        this.processedEvents = processedEvents;
        this.cancelledOrders = cancelledOrders;
        this.courierRepository = courierRepository;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.events = events;
    }

    public DeliveryDto getDeliveryById(Long id, CurrentUser currentUser) {
        return deliveryMapper.toDto(visibleTo(currentUser, findByIdOrThrow(id)));
    }

    public DeliveryDto getDeliveryByOrderId(Long orderId, CurrentUser currentUser) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Delivery for order ID: " + orderId + " was not found"));
        return deliveryMapper.toDto(visibleTo(currentUser, delivery));
    }

    public Page<DeliveryDto> getAllDeliveries(Pageable pageable, CurrentUser currentUser) {
        if (!currentUser.seesEveryDelivery()) {
            throw new ForbiddenException("Only couriers and administrators can list deliveries");
        }
        return deliveryRepository.findAll(pageable).map(deliveryMapper::toDto);
    }

    @Transactional
    public DeliveryDto createDelivery(CreateDeliveryRequest request) {
        if (deliveryRepository.existsByOrderId(request.orderId())) {
            throw new DeliveryAlreadyExistsException(request.orderId());
        }
        if (cancelledOrders.contains(request.orderId())) {
            throw new OrderAlreadyCancelledException(request.orderId());
        }

        Delivery delivery;
        try {
            delivery = deliveryRepository.saveAndFlush(Delivery.forOrder(
                    request.orderId(), request.userId(), request.courierId(), request.estimatedDeliveryTime(),
                    clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw new DeliveryAlreadyExistsException(request.orderId());
        }

        occupyCourier(delivery.getCourierId(), delivery.getCreatedAt());
        log.info("Created delivery id={} for orderId={}", delivery.getId(), delivery.getOrderId());
        publishStatusChange(delivery);
        return deliveryMapper.toDto(delivery);
    }

    @Transactional
    public DeliveryDto updateDeliveryStatus(Long id, UpdateDeliveryStatusRequest request) {
        Delivery delivery = findByIdOrThrow(id);

        Instant now = clock.instant();
        if (request.courierId() != null && !request.courierId().equals(delivery.getCourierId())) {
            Long previousCourierId = delivery.getCourierId();
            delivery.assignCourier(request.courierId(), now);
            releaseCourier(previousCourierId, now);
            occupyCourier(request.courierId(), now);
        }
        if (request.status() != null && delivery.changeStatus(request.status(), now)) {
            log.info("Delivery {} status updated to {}", id, request.status());
            releaseCourierOfFinished(delivery, now);
            publishStatusChange(delivery);
        }
        return deliveryMapper.toDto(delivery);
    }

    @Transactional
    public void cancelDeliveryOfOrder(OrderCancelledEvent event) {
        if (!processedEvents.registerFirstDelivery(event.getEventId(), Topics.ORDER_CANCELLED)) {
            log.info("Event {} is already processed, skipping", event.getEventId());
            return;
        }
        Optional<Delivery> existing = deliveryRepository.findByOrderId(event.getOrderId());
        if (existing.isEmpty()) {
            cancelledOrders.remember(event.getOrderId(), clock.instant());
            log.info("Order {} is cancelled before a courier was assigned, delivery will not be created",
                    event.getOrderId());
            return;
        }
        Delivery delivery = existing.get();
        if (!delivery.acceptOrderEvent(event.getSequence())) {
            log.info("Event {} with sequence {} is older than the applied one, dropping",
                    event.getEventId(), event.getSequence());
            return;
        }
        if (delivery.isFinished()) {
            log.info("Delivery {} is already {}, order cancellation changes nothing",
                    delivery.getId(), delivery.getStatus());
            return;
        }
        Instant now = clock.instant();
        delivery.changeStatus(DeliveryStatus.CANCELLED, now);
        releaseCourierOfFinished(delivery, now);
        log.info("Delivery {} is cancelled together with order {}", delivery.getId(), event.getOrderId());
        publishStatusChange(delivery);
    }

    @Transactional
    public void markOrderConfirmed(PaymentCapturedEvent event) {
        if (!processedEvents.registerFirstDelivery(event.getEventId(), Topics.PAYMENT_CAPTURED)) {
            log.info("Event {} is already processed, skipping", event.getEventId());
            return;
        }
        deliveryRepository.findByOrderId(event.getOrderId())
                .filter(delivery -> delivery.markOrderConfirmed(clock.instant()))
                .ifPresentOrElse(
                        delivery -> log.info("Order {} is paid, delivery {} may be picked up",
                                event.getOrderId(), delivery.getId()),
                        () -> log.info("Order {} is paid, but there is no open delivery for it", event.getOrderId()));
    }

    private void occupyCourier(Long courierId, Instant now) {
        if (courierId != null) {
            courierRepository.findById(courierId).ifPresent(courier -> courier.occupy(now));
        }
    }

    private void releaseCourier(Long courierId, Instant now) {
        if (courierId != null) {
            courierRepository.findById(courierId).ifPresent(courier -> courier.release(now));
        }
    }

    private void releaseCourierOfFinished(Delivery delivery, Instant now) {
        if (delivery.isFinished()) {
            releaseCourier(delivery.getCourierId(), now);
        }
    }

    private void publishStatusChange(Delivery delivery) {
        DeliveryStatusUpdatedEvent event = DeliveryStatusUpdatedEvent.newBuilder()
                .setEventId(UUID.randomUUID())
                .setDeliveryId(delivery.getId())
                .setOrderId(delivery.getOrderId())
                .setStatus(delivery.getStatus())
                .setUpdatedAt(delivery.getUpdatedAt())
                .setSequence(delivery.nextEventSequence())
                .build();
        outbox.append(AGGREGATE_TYPE, delivery.getId(), Topics.DELIVERY_STATUS_UPDATED, event.getEventId(), event);
        countAfterCommit(delivery.getStatus());
        events.publishEvent(TrackingUpdate.status(delivery));
    }

    private void countAfterCommit(DeliveryStatus status) {
        Runnable count = () -> meterRegistry.counter(STATUS_CHANGED, STATUS_TAG, status.name()).increment();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            count.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                count.run();
            }
        });
    }

    private static Delivery visibleTo(CurrentUser currentUser, Delivery delivery) {
        if (!currentUser.seesEveryDelivery() && !delivery.belongsTo(currentUser.id())) {
            throw new ForbiddenException("Delivery " + delivery.getId() + " does not belong to the current user");
        }
        return delivery;
    }

    private Delivery findByIdOrThrow(Long id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Delivery with ID: " + id + " was not found"));
    }
}
