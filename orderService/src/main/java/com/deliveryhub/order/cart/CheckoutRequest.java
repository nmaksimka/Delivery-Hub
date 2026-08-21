package com.deliveryhub.order.cart;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CheckoutRequest(@NotNull(message = "Delivery address is required") @Positive Long addressId,
                              @PositiveOrZero BigDecimal expectedDeliveryFee) {
}
