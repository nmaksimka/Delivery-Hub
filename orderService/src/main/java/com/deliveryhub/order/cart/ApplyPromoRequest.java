package com.deliveryhub.order.cart;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplyPromoRequest(@NotBlank @Size(max = 64) String code) {
}
