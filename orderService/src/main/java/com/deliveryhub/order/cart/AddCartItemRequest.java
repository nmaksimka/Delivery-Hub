package com.deliveryhub.order.cart;

import com.deliveryhub.order.dto.OrderItemRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddCartItemRequest(
        @NotNull(message = "Restaurant ID is required")
        @Positive(message = "Restaurant ID must be positive") Long restaurantId,
        @NotNull(message = "Menu item ID is required")
        @Positive(message = "Menu item ID must be positive") Long menuItemId,
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = OrderItemRequest.MAX_QUANTITY, message = "Quantity must not exceed 100") Integer quantity) {
}
