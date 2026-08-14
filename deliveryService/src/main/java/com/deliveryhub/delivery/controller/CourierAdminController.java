package com.deliveryhub.delivery.controller;

import com.deliveryhub.delivery.dto.CourierDto;
import com.deliveryhub.delivery.dto.RegisterCourierRequest;
import com.deliveryhub.delivery.entity.CourierStatus;
import com.deliveryhub.delivery.security.CurrentUser;
import com.deliveryhub.delivery.service.CourierService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/couriers")
public class CourierAdminController {

    private final CourierService courierService;

    public CourierAdminController(CourierService courierService) {
        this.courierService = courierService;
    }

    @GetMapping
    public Page<CourierDto> list(@RequestParam(required = false) CourierStatus status,
                                 @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC)
                                 Pageable pageable,
                                 @RequestHeader("X-User-Id") Long userId,
                                 @RequestHeader(name = "X-User-Role", defaultValue = "USER") String role) {
        return courierService.list(new CurrentUser(userId, role), status, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourierDto register(@Valid @RequestBody RegisterCourierRequest request,
                               @RequestHeader("X-User-Id") Long userId,
                               @RequestHeader(name = "X-User-Role", defaultValue = "USER") String role) {
        return courierService.register(new CurrentUser(userId, role), request);
    }
}
