package com.deliveryHub.delivery.controller;

import com.deliveryHub.delivery.dto.CreateDeliveryRequest;
import com.deliveryHub.delivery.dto.DeliveryDto;
import com.deliveryHub.delivery.dto.UpdateDeliveryStatusRequest;
import com.deliveryHub.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {
    private final DeliveryService deliveryService;

    @GetMapping
    public List<DeliveryDto> getAllDeliveries() {
        return deliveryService.getAllDeliveries();
    }

    @GetMapping("/id")
    public DeliveryDto getDeliveryById(@PathVariable Long deliveryId) {
        return deliveryService.getDeliveryById(deliveryId);
    }

    @GetMapping("/order/{orderId}")
    public DeliveryDto getDeliveryByOrderId(@PathVariable Long orderId) {
        return deliveryService.getDeliveryByOrderId(orderId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryDto createDelivery(@Valid @RequestBody CreateDeliveryRequest createDeliveryRequest) {
        return deliveryService.createDelivery(createDeliveryRequest);
    }

    @PatchMapping("/{id}/status")
    public DeliveryDto updateDeliveryStatus(@PathVariable Long id,
                                            @Valid @RequestBody UpdateDeliveryStatusRequest updateDeliveryStatusRequest) {
        return deliveryService.updateDeliveryStatus(id, updateDeliveryStatusRequest);
    }
}
