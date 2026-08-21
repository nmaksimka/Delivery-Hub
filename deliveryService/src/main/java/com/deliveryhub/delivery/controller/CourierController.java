package com.deliveryhub.delivery.controller;

import com.deliveryhub.delivery.dto.CourierDto;
import com.deliveryhub.delivery.dto.UpdateCourierLocationRequest;
import com.deliveryhub.delivery.dto.UpdateCourierStatusRequest;
import com.deliveryhub.delivery.entity.GeoPosition;
import com.deliveryhub.delivery.security.CurrentUser;
import com.deliveryhub.delivery.service.CourierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/couriers/me")
public class CourierController {

    private final CourierService courierService;

    public CourierController(CourierService courierService) {
        this.courierService = courierService;
    }

    @PutMapping("/status")
    public CourierDto changeStatus(@Valid @RequestBody UpdateCourierStatusRequest request,
                                   @RequestHeader("X-User-Id") Long userId,
                                   @RequestHeader(name = "X-User-Role", defaultValue = "USER") String role) {
        return courierService.changeOwnStatus(new CurrentUser(userId, role), request.status());
    }

    @PutMapping("/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeLocation(@Valid @RequestBody UpdateCourierLocationRequest request,
                               @RequestHeader("X-User-Id") Long userId,
                               @RequestHeader(name = "X-User-Role", defaultValue = "USER") String role) {
        courierService.moveOwnPosition(new CurrentUser(userId, role),
                new GeoPosition(request.latitude(), request.longitude()));
    }
}
