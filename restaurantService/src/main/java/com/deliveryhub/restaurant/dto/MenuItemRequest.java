package com.deliveryhub.restaurant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MenuItemRequest(
        @NotBlank(message = "Name is required") @Size(max = 255) String name,
        String description,
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be positive") BigDecimal price,
        @Size(max = 100) String category,
        Boolean available) {

    public boolean availableOrDefault() {
        return available == null || available;
    }
}
