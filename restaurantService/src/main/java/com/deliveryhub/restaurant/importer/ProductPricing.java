package com.deliveryhub.restaurant.importer;

import com.deliveryhub.restaurant.entity.MenuSection;

import java.math.BigDecimal;

public final class ProductPricing {

    private static final int PRODUCT_BASE = 90;
    private static final int DRINK_BASE = 70;
    private static final int STEP = 5;
    private static final int STEPS = 20;

    private ProductPricing() {
    }

    public static BigDecimal priceOf(String code, MenuSection section) {
        int base = section == MenuSection.DRINK ? DRINK_BASE : PRODUCT_BASE;
        return BigDecimal.valueOf(base + Math.floorMod(code.hashCode(), STEPS) * STEP).setScale(2);
    }
}
