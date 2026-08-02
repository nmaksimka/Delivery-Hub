package com.deliveryhub.order.promo;

import java.math.BigDecimal;

public record PromoUse(Long promoId, String code, BigDecimal discount) {
}
