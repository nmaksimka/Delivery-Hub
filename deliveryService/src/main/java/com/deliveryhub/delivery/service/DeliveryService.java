package com.deliveryhub.delivery.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.delivery.dto.CreateDeliveryRequest;
import com.deliveryhub.delivery.dto.DeliveryDto;
import com.deliveryhub.delivery.dto.UpdateDeliveryStatusRequest;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.event.DeliveryStatusChangedDomainEvent;
import com.deliveryhub.delivery.mapper.DeliveryMapper;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final DeliveryRepository deliveryRepository;
    private final DeliveryMapper deliveryMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    public DeliveryService(DeliveryRepository deliveryRepository,
                           DeliveryMapper deliveryMapper,
                           ApplicationEventPublisher applicationEventPublisher) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryMapper = deliveryMapper;
        this.applicationEventPublisher = applicationEventPublisher;
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
        if (deliveryRepository.existsByOrderId(request.getOrderId())) {
            throw new IllegalStateException("Delivery already exists for order ID: " + request.getOrderId());
        }

        Delivery delivery = deliveryRepository.save(Delivery.builder()
                .orderId(request.getOrderId())
                .courierId(request.getCourierId())
                .status(DeliveryStatus.ASSIGNED)
                .estimatedDeliveryTime(request.getEstimatedDeliveryTime())
                .build());

        log.info("Created delivery id={} for orderId={}", delivery.getId(), delivery.getOrderId());
        publishStatusChange(delivery);
        return deliveryMapper.toDto(delivery);
    }

    /**
     * Раньше ручная смена статуса никуда не публиковалась, и заказ о ней не узнавал.
     */
    @Transactional
    public DeliveryDto updateDeliveryStatus(Long id, UpdateDeliveryStatusRequest request) {
        Delivery delivery = findByIdOrThrow(id);

        delivery.setStatus(request.getStatus());
        if (request.getCourierId() != null) {
            delivery.setCourierId(request.getCourierId());
        }

        log.info("Delivery {} status updated to {}", id, request.getStatus());
        publishStatusChange(delivery);
        return deliveryMapper.toDto(delivery);
    }

    /** Идемпотентно: повторная доставка того же OrderCreatedEvent не создаёт дубль. */
    @Transactional
    public void createDeliveryFromOrderEvent(OrderCreatedEvent event) {
        if (deliveryRepository.existsByOrderId(event.orderId())) {
            log.warn("Delivery already exists for orderId={}, skipping", event.orderId());
            return;
        }

        Delivery delivery = deliveryRepository.save(Delivery.builder()
                .orderId(event.orderId())
                .status(DeliveryStatus.ASSIGNED)
                .build());

        log.info("Created delivery id={} from order event, orderId={}", delivery.getId(), event.orderId());
        publishStatusChange(delivery);
    }

    private void publishStatusChange(Delivery delivery) {
        applicationEventPublisher.publishEvent(new DeliveryStatusChangedDomainEvent(
                new DeliveryStatusUpdatedEvent(
                        delivery.getId(), delivery.getOrderId(), delivery.getStatus(), delivery.getUpdatedAt())));
    }

    private Delivery findByIdOrThrow(Long id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Delivery with ID: " + id + " was not found"));
    }
}
