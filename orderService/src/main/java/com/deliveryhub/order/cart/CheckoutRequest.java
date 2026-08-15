package com.deliveryhub.order.cart;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CheckoutRequest(@NotNull(message = "Delivery address is required") @Positive Long addressId) {
}
