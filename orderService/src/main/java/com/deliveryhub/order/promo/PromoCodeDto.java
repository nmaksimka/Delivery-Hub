package com.deliveryhub.order.promo;

import java.math.BigDecimal;
import java.time.Instant;

public record PromoCodeDto(
        Long id,
        String code,
        PromoType type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        Instant validFrom,
        Instant validTo,
        Integer maxRedemptions,
        Integer maxPerUser,
        Long restaurantId,
        boolean active,
        int usedCount,
        Instant createdAt) {

    public static PromoCodeDto of(PromoCode promo) {
        return new PromoCodeDto(promo.getId(), promo.getCode(), promo.getType(), promo.getValue(),
                promo.getMinOrderAmount(), promo.getValidFrom(), promo.getValidTo(), promo.getMaxRedemptions(),
                promo.getMaxPerUser(), promo.getRestaurantId(), promo.isActive(), promo.getUsedCount(),
                promo.getCreatedAt());
    }
}
