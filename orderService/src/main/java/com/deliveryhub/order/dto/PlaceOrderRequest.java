package com.deliveryhub.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PlaceOrderRequest(
        @NotNull(message = "Restaurant ID is required")
        @Positive(message = "Restaurant ID must be positive") Long restaurantId,
        @NotEmpty(message = "Order must contain at least one item")
        @Size(max = 50, message = "Order must not contain more than 50 lines")
        @Valid List<OrderItemRequest> orderItemsRequest,
        @NotNull(message = "Address ID is required")
        @Positive(message = "Address ID must be positive") Long addressId) {

    public CreateOrderRequest toCreateRequest() {
        return new CreateOrderRequest(restaurantId, orderItemsRequest);
    }
}
