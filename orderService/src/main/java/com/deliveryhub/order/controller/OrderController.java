package com.deliveryhub.order.controller;

import com.deliveryhub.order.dto.CreateOrderRequest;
import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.dto.UpdateOrderStatusRequest;
import com.deliveryhub.order.security.CurrentUser;
import com.deliveryhub.order.service.OrderService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @GetMapping("/{id}")
    public OrderDto getOrderById(@PathVariable Long id,
                                 @RequestHeader("X-User-Id") Long userId,
                                 @RequestHeader(name = "X-User-Role", defaultValue = "USER") String role) {
        return orderService.getOrderById(id, new CurrentUser(userId, role));
    }

    /** Заказы текущего пользователя. Чужие заказы через этот эндпоинт недоступны. */
    @GetMapping
    public Page<OrderDto> getMyOrders(@RequestHeader("X-User-Id") Long userId,
                                      @PageableDefault(size = 20) Pageable pageable) {
        return orderService.getOrdersOfUser(userId, pageable);
    }

    /** Только для ADMIN: доступ ограничен на уровне API Gateway. */
    @GetMapping("/all")
    public Page<OrderDto> getAllOrders(@PageableDefault(size = 20) Pageable pageable) {
        return orderService.getAllOrders(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto createOrder(@Valid @RequestBody CreateOrderRequest createOrderRequest,
                                @RequestHeader("X-User-Id") Long userId) {
        return orderService.createOrder(createOrderRequest, userId);
    }

    @PatchMapping("/{id}/status")
    public OrderDto updateOrderStatus(@PathVariable Long id,
                                      @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateOrderStatus(id, request.getStatus());
    }
}
