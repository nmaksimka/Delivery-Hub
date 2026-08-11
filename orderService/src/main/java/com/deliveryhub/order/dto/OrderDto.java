package com.deliveryhub.order.dto;

import com.deliveryhub.order.entity.CancellationReason;
import com.deliveryhub.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDto(
        Long id,
        Long userId,
        Long restaurantId,
        OrderStatus status,
        CancellationReason cancellationReason,
        Instant refundedAt,
        BigDecimal totalAmount,
        BigDecimal discount,
        String promoCode,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemDto> orderItems) {
}
