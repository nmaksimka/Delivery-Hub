package com.deliveryhub.delivery.service;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.delivery.dto.CreateDeliveryRequest;
import com.deliveryhub.delivery.dto.DeliveryDto;
import com.deliveryhub.delivery.dto.UpdateDeliveryStatusRequest;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.inbox.ProcessedEvents;
import com.deliveryhub.delivery.mapper.DeliveryMapper;
import com.deliveryhub.delivery.outbox.Outbox;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final String AGGREGATE_TYPE = "Delivery";

    private final DeliveryRepository deliveryRepository;
    private final DeliveryMapper deliveryMapper;
    private final Outbox outbox;
    private final ProcessedEvents processedEvents;
    private final Clock clock;

    public DeliveryService(DeliveryRepository deliveryRepository,
                           DeliveryMapper deliveryMapper,
                           Outbox outbox,
                           ProcessedEvents processedEvents,
                           Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryMapper = deliveryMapper;
        this.outbox = outbox;
        this.processedEvents = processedEvents;
        this.clock = clock;
    }

    public DeliveryDto getDeliveryById(Long id) {
        return deliveryMapper.toDto(findByIdOrThrow(id));
    }

    public DeliveryDto getDeliveryByOrderId(Long orderId) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Delivery for order ID: " + orderId + " was not found"));
        return deliveryMapper.toDto(delivery);
    }

    public Page<DeliveryDto> getAllDeliveries(Pageable pageable) {
        return deliveryRepository.findAll(pageable).map(deliveryMapper::toDto);
    }

    @Transactional
    public DeliveryDto createDelivery(CreateDeliveryRequest request) {
        if (deliveryRepository.existsByOrderId(request.orderId())) {
            throw new IllegalStateException("Delivery already exists for order ID: " + request.orderId());
        }

        Delivery delivery = deliveryRepository.save(Delivery.forOrder(
                request.orderId(), request.courierId(), request.estimatedDeliveryTime()));

        log.info("Created delivery id={} for orderId={}", delivery.getId(), delivery.getOrderId());
        publishStatusChange(delivery);
        return deliveryMapper.toDto(delivery);
    }

    @Transactional
    public DeliveryDto updateDeliveryStatus(Long id, UpdateDeliveryStatusRequest request) {
        Delivery delivery = findByIdOrThrow(id);

        if (request.courierId() != null) {
            delivery.assignCourier(request.courierId());
        }
        delivery.changeStatus(request.status());

        log.info("Delivery {} status updated to {}", id, request.status());
        publishStatusChange(delivery);
        return deliveryMapper.toDto(delivery);
    }

    @Transactional
    public void createDeliveryFromOrderEvent(OrderCreatedEvent event) {
        if (!processedEvents.registerFirstDelivery(event.eventId(), Topics.ORDER_CREATED)) {
            log.info("Event {} is already processed, skipping", event.eventId());
            return;
        }
        if (deliveryRepository.existsByOrderId(event.orderId())) {
            log.warn("Delivery already exists for orderId={}, skipping", event.orderId());
            return;
        }

        Delivery delivery = deliveryRepository.save(Delivery.forOrder(event.orderId()));

        log.info("Created delivery id={} from order event, orderId={}", delivery.getId(), event.orderId());
        publishStatusChange(delivery);
    }

    private void publishStatusChange(Delivery delivery) {
        outbox.append(AGGREGATE_TYPE, delivery.getId(), Topics.DELIVERY_STATUS_UPDATED,
                new DeliveryStatusUpdatedEvent(UUID.randomUUID(), delivery.getId(), delivery.getOrderId(),
                        delivery.getStatus(), clock.instant()));
    }

    private Delivery findByIdOrThrow(Long id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Delivery with ID: " + id + " was not found"));
    }
}
