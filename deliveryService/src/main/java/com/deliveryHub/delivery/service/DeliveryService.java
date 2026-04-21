package com.deliveryHub.delivery.service;

import com.deliveryHub.delivery.dto.CreateDeliveryRequest;
import com.deliveryHub.delivery.dto.DeliveryDto;
import com.deliveryHub.delivery.dto.UpdateDeliveryStatusRequest;
import com.deliveryHub.delivery.entity.Delivery;
import com.deliveryHub.delivery.mapper.DeliveryMapper;
import com.deliveryHub.delivery.repository.DeliveryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryService {
    private final DeliveryRepository deliveryRepository;
    private final DeliveryMapper deliveryMapper;

    public DeliveryDto getDeliveryById(Long id) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Delivery with ID: " + id + " was not found"));

        return deliveryMapper.toDto(delivery);
    }

    public DeliveryDto getDeliveryByOrderId(Long id) {
        Delivery delivery = deliveryRepository.findByOrderId(id)
                .orElseThrow(() -> new EntityNotFoundException("Delivery with order ID: " + id + " was not found"));

        return deliveryMapper.toDto(delivery);
    }

    public List<DeliveryDto> getAllDeliveries() {
        return deliveryRepository.findAll().stream()
                .map(deliveryMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    public DeliveryDto createDelivery(CreateDeliveryRequest createDeliveryRequest) {
        deliveryRepository.findByOrderId(createDeliveryRequest.getOrderId()).ifPresent(delivery -> {
            throw new IllegalStateException("Delivery already exists for order ID: " + createDeliveryRequest.getOrderId());
        });

        Delivery delivery = Delivery.builder()
                .orderId(createDeliveryRequest.getOrderId())
                .courierId(createDeliveryRequest.getCourierId())
                .status("ASSIGNED")
                .estimatedDeliveryTime(createDeliveryRequest.getEstimatedDeliveryTime())
                .build();

        Delivery savedDelivery = deliveryRepository.save(delivery);
        log.info("Created delivery with id: {} for order id: {}", savedDelivery.getId(), savedDelivery.getOrderId());

        return deliveryMapper.toDto(savedDelivery);
    }

    @Transactional
    public DeliveryDto updateDeliveryStatus(Long id, UpdateDeliveryStatusRequest updateDeliveryStatusRequest) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Delivery with ID: " + id + " was not found"));

        delivery.setStatus(updateDeliveryStatusRequest.getStatus());

        log.info("Updated delivery with id:{} status to '{}'", id, updateDeliveryStatusRequest.getStatus());
        return deliveryMapper.toDto(delivery);
    }
}
