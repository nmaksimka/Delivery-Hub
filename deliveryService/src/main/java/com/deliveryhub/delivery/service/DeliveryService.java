package com.deliveryhub.delivery.service;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.delivery.dto.CreateDeliveryRequest;
import com.deliveryhub.delivery.dto.DeliveryDto;
import com.deliveryhub.delivery.dto.UpdateDeliveryStatusRequest;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.exception.DeliveryAlreadyExistsException;
import com.deliveryhub.delivery.exception.ForbiddenException;
import com.deliveryhub.delivery.inbox.ProcessedEvents;
import com.deliveryhub.delivery.mapper.DeliveryMapper;
import com.deliveryhub.delivery.outbox.Outbox;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import com.deliveryhub.delivery.security.CurrentUser;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
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
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public DeliveryService(DeliveryRepository deliveryRepository,
                           DeliveryMapper deliveryMapper,
                           Outbox outbox,
                           ProcessedEvents processedEvents,
                           Clock clock,
                           MeterRegistry meterRegistry) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryMapper = deliveryMapper;
        this.outbox = outbox;
        this.processedEvents = processedEvents;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
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

        Delivery delivery;
        try {
            delivery = deliveryRepository.saveAndFlush(Delivery.forOrder(
                    request.orderId(), request.userId(), request.courierId(), request.estimatedDeliveryTime(),
                    clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw new DeliveryAlreadyExistsException(request.orderId());
        }

        log.info("Created delivery id={} for orderId={}", delivery.getId(), delivery.getOrderId());
        publishStatusChange(delivery);
        return deliveryMapper.toDto(delivery);
    }

    @Transactional
    public DeliveryDto updateDeliveryStatus(Long id, UpdateDeliveryStatusRequest request) {
        Delivery delivery = findByIdOrThrow(id);

        Instant now = clock.instant();
        if (request.courierId() != null) {
            delivery.assignCourier(request.courierId(), now);
        }
        if (request.status() != null && delivery.changeStatus(request.status(), now)) {
            log.info("Delivery {} status updated to {}", id, request.status());
            publishStatusChange(delivery);
        }
        return deliveryMapper.toDto(delivery);
    }

    @Transactional
    public void createDeliveryFromOrderEvent(OrderCreatedEvent event) {
        if (!processedEvents.registerFirstDelivery(event.getEventId(), Topics.ORDER_CREATED)) {
            log.info("Event {} is already processed, skipping", event.getEventId());
            return;
        }
        if (deliveryRepository.existsByOrderId(event.getOrderId())) {
            log.warn("Delivery already exists for orderId={}, skipping", event.getOrderId());
            return;
        }

        Delivery delivery = deliveryRepository.save(Delivery.forOrder(event.getOrderId(), event.getUserId(), clock.instant()));

        log.info("Created delivery id={} from order event, orderId={}", delivery.getId(), event.getOrderId());
        publishStatusChange(delivery);
    }

    private void publishStatusChange(Delivery delivery) {
        DeliveryStatusUpdatedEvent event = DeliveryStatusUpdatedEvent.newBuilder()
                .setEventId(UUID.randomUUID())
                .setDeliveryId(delivery.getId())
                .setOrderId(delivery.getOrderId())
                .setStatus(delivery.getStatus())
                .setUpdatedAt(delivery.getUpdatedAt())
                .build();
        outbox.append(AGGREGATE_TYPE, delivery.getId(), Topics.DELIVERY_STATUS_UPDATED, event.getEventId(), event);
        meterRegistry.counter(STATUS_CHANGED, STATUS_TAG, delivery.getStatus().name()).increment();
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
