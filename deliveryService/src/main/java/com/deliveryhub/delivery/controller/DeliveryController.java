package com.deliveryhub.delivery.controller;

import com.deliveryhub.delivery.dto.CreateDeliveryRequest;
import com.deliveryhub.delivery.dto.DeliveryDto;
import com.deliveryhub.delivery.dto.UpdateDeliveryStatusRequest;
import com.deliveryhub.delivery.security.CurrentUser;
import com.deliveryhub.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {
    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping
    public Page<DeliveryDto> getAllDeliveries(@PageableDefault(size = 20) Pageable pageable,
                                              @RequestHeader("X-User-Id") Long userId,
                                              @RequestHeader(name = "X-User-Role", defaultValue = "USER") String role) {
        return deliveryService.getAllDeliveries(pageable, new CurrentUser(userId, role));
    }

    @GetMapping("/{id}")
    public DeliveryDto getDeliveryById(@PathVariable Long id,
                                       @RequestHeader("X-User-Id") Long userId,
                                       @RequestHeader(name = "X-User-Role", defaultValue = "USER") String role) {
        return deliveryService.getDeliveryById(id, new CurrentUser(userId, role));
    }

    @GetMapping("/order/{orderId}")
    public DeliveryDto getDeliveryByOrderId(@PathVariable Long orderId,
                                            @RequestHeader("X-User-Id") Long userId,
                                            @RequestHeader(name = "X-User-Role", defaultValue = "USER") String role) {
        return deliveryService.getDeliveryByOrderId(orderId, new CurrentUser(userId, role));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryDto createDelivery(@Valid @RequestBody CreateDeliveryRequest request,
                                      @RequestHeader("X-User-Id") Long userId,
                                      @RequestHeader(name = "X-User-Role", defaultValue = "USER") String role) {
        return deliveryService.createDeliveryAs(request, new CurrentUser(userId, role));
    }

    @PatchMapping("/{id}/status")
    public DeliveryDto updateDeliveryStatus(@PathVariable Long id,
                                            @Valid @RequestBody UpdateDeliveryStatusRequest request,
                                            @RequestHeader("X-User-Id") Long userId,
                                            @RequestHeader(name = "X-User-Role", defaultValue = "USER") String role) {
        return deliveryService.updateDeliveryStatusAs(id, request, new CurrentUser(userId, role));
    }
}
