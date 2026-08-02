package com.deliveryhub.order.dto;

import com.deliveryhub.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDto(
        Long id,
        Long userId,
        Long restaurantId,
        OrderStatus status,
        BigDecimal totalAmount,
        BigDecimal discount,
        String promoCode,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemDto> orderItems) {
}
