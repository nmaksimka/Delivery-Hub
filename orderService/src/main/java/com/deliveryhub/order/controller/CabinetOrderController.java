package com.deliveryhub.order.controller;

import com.deliveryhub.order.client.RestaurantOwnership;
import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cabinet/orders")
public class CabinetOrderController {

    private final OrderService orderService;
    private final RestaurantOwnership ownership;

    public CabinetOrderController(OrderService orderService, RestaurantOwnership ownership) {
        this.orderService = orderService;
        this.ownership = ownership;
    }

    @GetMapping
    public Page<OrderDto> ordersOfRestaurant(@RequestHeader("X-User-Id") Long userId,
                                             @RequestParam Long restaurantId,
                                             @PageableDefault(size = 20, sort = "createdAt",
                                                     direction = Sort.Direction.DESC) Pageable pageable) {
        ownership.requireOwner(userId, restaurantId);
        return orderService.getOrdersOfRestaurant(restaurantId, pageable);
    }
}
