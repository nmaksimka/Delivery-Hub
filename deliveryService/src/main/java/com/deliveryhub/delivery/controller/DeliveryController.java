package com.deliveryhub.delivery.controller;

import com.deliveryhub.delivery.dto.CreateDeliveryRequest;
import com.deliveryhub.delivery.dto.DeliveryDto;
import com.deliveryhub.delivery.dto.UpdateDeliveryStatusRequest;
import com.deliveryhub.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {
    private final DeliveryService deliveryService;

    @GetMapping
    public Page<DeliveryDto> getAllDeliveries(@PageableDefault(size = 20) Pageable pageable) {
        return deliveryService.getAllDeliveries(pageable);
    }

    @GetMapping("/{id}")
    public DeliveryDto getDeliveryById(@PathVariable Long id) {
        return deliveryService.getDeliveryById(id);
    }

    @GetMapping("/order/{orderId}")
    public DeliveryDto getDeliveryByOrderId(@PathVariable Long orderId) {
        return deliveryService.getDeliveryByOrderId(orderId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryDto createDelivery(@Valid @RequestBody CreateDeliveryRequest request) {
        return deliveryService.createDelivery(request);
    }

    @PatchMapping("/{id}/status")
    public DeliveryDto updateDeliveryStatus(@PathVariable Long id,
                                            @Valid @RequestBody UpdateDeliveryStatusRequest request) {
        return deliveryService.updateDeliveryStatus(id, request);
    }
}
