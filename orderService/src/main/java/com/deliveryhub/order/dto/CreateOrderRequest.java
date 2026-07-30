package com.deliveryhub.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CreateOrderRequest(
        @NotNull(message = "Restaurant ID is required")
        @Positive(message = "Restaurant ID must be positive") Long restaurantId,
        @NotEmpty(message = "Order must contain at least one item")
        @Size(max = 50, message = "Order must not contain more than 50 lines")
        @Valid List<OrderItemRequest> orderItemsRequest) {

    public Map<Long, Integer> quantitiesByMenuItem() {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderItemRequest item : orderItemsRequest) {
            quantities.merge(item.menuItemId(), item.quantity(), Integer::sum);
        }
        return quantities;
    }
}
