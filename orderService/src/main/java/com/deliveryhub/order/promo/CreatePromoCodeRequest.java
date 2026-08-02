package com.deliveryhub.order.promo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record CreatePromoCodeRequest(
        @NotBlank @Size(max = 64) String code,
        @NotNull PromoType type,
        @NotNull @DecimalMin(value = "0.01") @DecimalMax("100000.00") BigDecimal value,
        @DecimalMin("0.00") @DecimalMax("100000.00") BigDecimal minOrderAmount,
        Instant validFrom,
        Instant validTo,
        @Positive Integer maxRedemptions,
        @Positive Integer maxPerUser,
        Long restaurantId) {
}
