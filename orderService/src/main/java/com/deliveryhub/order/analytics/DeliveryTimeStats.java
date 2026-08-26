package com.deliveryhub.order.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DeliveryTimeStats(
        LocalDate day,
        long orders,
        BigDecimal averageMinutes,
        BigDecimal medianMinutes,
        BigDecimal p90Minutes) {
}
