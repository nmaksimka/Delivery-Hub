package com.deliveryhub.order.cart;

import com.deliveryhub.order.dto.OrderItemRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ChangeQuantityRequest(
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = OrderItemRequest.MAX_QUANTITY, message = "Quantity must not exceed 100") Integer quantity) {
}
